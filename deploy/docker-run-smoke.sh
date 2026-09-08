#!/bin/bash
# =============================================================================
# docker-run-smoke.sh —— tool-sandbox 交付冒烟脚本
#
# 在具备 Docker 的主机（Linux 原生 / WSL2 / Git Bash + Docker Desktop）上运行：
#   bash deploy/docker-run-smoke.sh
#
# 步骤：
#   1. docker build -t tool-sandbox:latest <repo根目录>
#   2. 依次真实运行 python / node / bash / java 四类 runtime 容器，断言 I/O 契约：
#      - 脚本 argv[1]=/data（数据目录）、argv[2:]=数据文件列表
#      - 环境变量 DATA_DIR=/data、RESULT_DIR=/result 均正确注入
#      - 脚本把结果写回 $RESULT_DIR/out.txt，宿主侧读到内容
#   3. 断网验证：--network none 下容器内 curl/wget/ping 全部失败，而脚本自身输出正常
#   4. 非 root 验证：容器内 id -u != 0
#   5. 超时杀验证（可选）：容器内 sleep 超过外层 timeout 绑定时间即被杀，不长期挂起
#
# 注意：
#   - bat/cmd 为 Windows 运行时，Linux 沙箱内不支持，故不在冒烟范围内。
#   - Git Bash 下宿主机路径经 cygpath -w 转换为 Windows 路径供 Docker Desktop 挂载。
# =============================================================================

set -u

PASS=0
FAIL=0

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
IMAGE="tool-sandbox:latest"

say()  { printf '%s\n' "$*"; }
ok()   { PASS=$((PASS+1)); printf '[PASS] %s\n' "$1"; }
bad()  { FAIL=$((FAIL+1)); printf '[FAIL] %s\n' "$1"; }

# Git Bash (Windows) 下把 POSIX 路径转成 Docker Desktop 可用的 Windows 路径
if command -v cygpath >/dev/null 2>&1; then
    host_path() { cygpath -w -- "$1"; }
else
    host_path() { printf '%s' "$1"; }
fi

# ---------------------------------------------------------------------------
# 一次沙箱运行的公共封装 —— 参数与 SandboxExecutionService.buildDockerRunArgs
# 逐项对应：--network none / --memory 512m / --cpus 1 / --user 1000:1000 / --rm
# 三个 bind mount（/data 只读、/result 可写、/script 只读）+ 三个 -e env，
# 最后按 <image> <launcher> <runtime> <script> <dataDir> [files...] 执行。
# ---------------------------------------------------------------------------
run_sandbox_job() {
    local runtime="$1" script_name="$2" script_dir="$3" data_dir="$4" result_dir="$5"
    shift 5
    docker run --rm --network none --memory 512m --cpus 1 --user 1000:1000 \
        --mount "type=bind,source=$(host_path "$script_dir"),target=/script,readonly" \
        --mount "type=bind,source=$(host_path "$data_dir"),target=/data,readonly" \
        --mount "type=bind,source=$(host_path "$result_dir"),target=/result" \
        -e DATA_DIR=/data \
        -e 'INPUT_FILES=["/data/input.txt"]' \
        -e RESULT_DIR=/result \
        "$IMAGE" /usr/local/bin/launcher "$runtime" "/script/$script_name" "/data" "$@"
}

# 新建一次用例的工作目录：script/ data/ result/ + 一个数据文件
new_job_dir() {
    local work
    work="$(mktemp -d)"
    mkdir -p "$work/script" "$work/data" "$work/result" || { echo "mktemp 失败" >&2; exit 1; }
    printf 'hello-smoke\n' > "$work/data/input.txt"
    printf '%s\n' "$work"
}

# 统一断言：退出码为 0 且 $result/out.txt 内含期望标记
assert_result() {
    local name="$1" exit_code="$2" result_dir="$3" marker="$4"
    if [ "$exit_code" -ne 0 ]; then
        bad "$name: 容器退出码=$exit_code（期望 0）"
        return 1
    fi
    if [ -f "$result_dir/out.txt" ] && grep -q -- "$marker" "$result_dir/out.txt" 2>/dev/null; then
        ok "$name: 退出码 0 + 结果回传 $RESULT_DIR/out.txt 含 '$marker'"
        return 0
    fi
    bad "$name: 结果文件 out.txt 缺失或不含 '$marker'"
    return 1
}

cleanup_job() {
    [ -n "${1-}" ] && rm -rf -- "$1"
}

# ===========================================================================
# 0) 构建镜像
# ===========================================================================
say ""
say "== [0/6] 构建镜像 tool-sandbox:latest（build context = 仓库根目录） =="
if docker build -t "$IMAGE" "$ROOT_DIR"; then
    ok "docker build -t $IMAGE $ROOT_DIR"
else
    bad "docker build 失败（需联网拉取基础镜像，见 docs/部署与沙箱运维.md）"
    echo ""; echo "冒烟结果: $PASS 通过 / $FAIL 失败"
    exit 1
fi

# ===========================================================================
# 1) python
# ===========================================================================
say ""
say "== [1/6] python 运行时 =="
PY_WORK="$(new_job_dir)"
cat > "$PY_WORK/script/main.py" <<'PYEOF'
import json, os, sys
data_dir = sys.argv[1]
files = sys.argv[2:]
paths = json.loads(os.environ["INPUT_FILES"])
assert data_dir == "/data", "argv[1] != /data"
assert os.environ["DATA_DIR"] == "/data", "DATA_DIR != /data"
assert os.environ["RESULT_DIR"] == "/result", "RESULT_DIR != /result"
print("arg1=" + data_dir)
first_text = ""
for f in files:
    with open(f) as fh:
        first_text = fh.read().strip()
        print("file=" + f + " content=" + first_text)
assert files and first_text == "hello-smoke", "数据文件未按 argv 读入"
with open(os.path.join(os.environ["RESULT_DIR"], "out.txt"), "w") as fh:
    fh.write("SMOKE-python-OK\n")
PYEOF
PY_EXIT=0
docker_output="$(run_sandbox_job python main.py "$PY_WORK/script" "$PY_WORK/data" "$PY_WORK/result" /data/input.txt)" || PY_EXIT=$?
say "$docker_output"
assert_result "python" "$PY_EXIT" "$PY_WORK/result" "SMOKE-python-OK" || true
cleanup_job "$PY_WORK"

# ===========================================================================
# 2) node
# ===========================================================================
say ""
say "== [2/6] node 运行时 =="
NODE_WORK="$(new_job_dir)"
cat > "$NODE_WORK/script/main.js" <<'NODEEOF'
const fs = require("fs");
const dataDir = process.argv[2];
const files = process.argv.slice(3);
const paths = JSON.parse(process.env.INPUT_FILES);
if (dataDir !== "/data") throw new Error("argv[2] != /data");
if (process.env.DATA_DIR !== "/data") throw new Error("DATA_DIR != /data");
if (process.env.RESULT_DIR !== "/result") throw new Error("RESULT_DIR != /result");
console.log("arg1=" + dataDir);
let firstText = "";
for (const f of files) {
    firstText = fs.readFileSync(f, "utf8").trim();
    console.log("file=" + f + " content=" + firstText);
}
if (!files.length || firstText !== "hello-smoke") throw new Error("数据文件未按 argv 读入");
fs.writeFileSync(process.env.RESULT_DIR + "/out.txt", "SMOKE-node-OK\n");
NODEEOF
NODE_EXIT=0
docker_output="$(run_sandbox_job node main.js "$NODE_WORK/script" "$NODE_WORK/data" "$NODE_WORK/result" /data/input.txt)" || NODE_EXIT=$?
say "$docker_output"
assert_result "node" "$NODE_EXIT" "$NODE_WORK/result" "SMOKE-node-OK" || true
cleanup_job "$NODE_WORK"

# ===========================================================================
# 3) bash
# ===========================================================================
say ""
say "== [3/6] bash 运行时 =="
BASH_WORK="$(new_job_dir)"
cat > "$BASH_WORK/script/main.sh" <<'BASHEOF'
#!/bin/bash
data_dir="$1"; shift
[ "$data_dir" = "/data" ] || exit 9
[ "${DATA_DIR}" = "/data" ] || exit 9
[ "${RESULT_DIR}" = "/result" ] || exit 9
echo "arg1=$data_dir"
first_text=""
for f in "$@"; do
    first_text="$(cat "$f")"
    echo "file=$f content=$first_text"
done
[ -n "$first_text" ] || exit 9
echo "SMOKE-bash-OK"
echo "SMOKE-bash-OK" > "$RESULT_DIR/out.txt"
exit 0
BASHEOF
BASH_EXIT=0
docker_output="$(run_sandbox_job bash main.sh "$BASH_WORK/script" "$BASH_WORK/data" "$BASH_WORK/result" /data/input.txt)" || BASH_EXIT=$?
say "$docker_output"
assert_result "bash" "$BASH_EXIT" "$BASH_WORK/result" "SMOKE-bash-OK" || true
cleanup_job "$BASH_WORK"

# ===========================================================================
# 4) java —— 用沙箱镜像自身完成 javac + jar 打包（宿主机无需装 JDK）
#    先测无 lib/ 的 -jar 路径；再测带 lib/ 的 -cp Main-Class 解析路径
# ===========================================================================
say ""
say "== [4/6] java 运行时 =="
JAVA_BUILD="$(mktemp -d)"
cat > "$JAVA_BUILD/Main.java" <<'JAVASRC'
import java.nio.file.Files;
import java.nio.file.Paths;
public class Main {
    public static void main(String[] args) throws Exception {
        String dataDir = args[0];
        if (!"/data".equals(dataDir)) throw new RuntimeException("argv[0] != /data");
        if (!"/data".equals(System.getenv("DATA_DIR"))) throw new RuntimeException("DATA_DIR != /data");
        if (!"/result".equals(System.getenv("RESULT_DIR"))) throw new RuntimeException("RESULT_DIR != /result");
        System.out.println("arg1=" + dataDir);
        String firstText = "";
        for (int i = 1; i < args.length; i++) {
            String text = new String(Files.readAllBytes(Paths.get(args[i]))).trim();
            firstText = text;
            System.out.println("file=" + args[i] + " content=" + text);
        }
        if (args.length < 2 || !"hello-smoke".equals(firstText)) throw new RuntimeException("数据文件未按 argv 读入");
        Files.write(Paths.get(System.getenv("RESULT_DIR"), "out.txt"), "SMOKE-java-OK\n".getBytes());
        System.out.println("SMOKE-java-OK");
    }
}
JAVASRC
if docker run --rm --memory 512m \
    --mount "type=bind,source=$(host_path "$JAVA_BUILD"),target=/build,readonly" \
    --user 1000:1000 -w /build "$IMAGE" \
    sh -c 'javac Main.java && jar cfe app.jar Main Main.class'; then
    ok "java 冒烟包构建成功（镜像内 javac + jar cfe）"
else
    bad "镜像内 javac/jar 打包失败（检查基础镜像是否含 JDK）"
fi
# 4a) -jar 路径（无 lib/）
JAVA_WORK="$(new_job_dir)"
cp "$JAVA_BUILD/app.jar" "$JAVA_WORK/script/app.jar"
JAVA_EXIT=0
docker_output="$(run_sandbox_job java app.jar "$JAVA_WORK/script" "$JAVA_WORK/data" "$JAVA_WORK/result" /data/input.txt)" || JAVA_EXIT=$?
say "$docker_output"
assert_result "java(-jar 无 lib)" "$JAVA_EXIT" "$JAVA_WORK/result" "SMOKE-java-OK" || true
cleanup_job "$JAVA_WORK"
# 4b) -cp Main-Class 解析路径（带 lib/）
JAVA_LIB_WORK="$(new_job_dir)"
cp "$JAVA_BUILD/app.jar" "$JAVA_LIB_WORK/script/app.jar"
mkdir -p "$JAVA_LIB_WORK/script/lib"
JAVA_LIB_EXIT=0
docker_output="$(run_sandbox_job java app.jar "$JAVA_LIB_WORK/script" "$JAVA_LIB_WORK/data" "$JAVA_LIB_WORK/result" /data/input.txt)" || JAVA_LIB_EXIT=$?
say "$docker_output"
assert_result "java(-cp 带 lib/ 解析 Main-Class)" "$JAVA_LIB_EXIT" "$JAVA_LIB_WORK/result" "SMOKE-java-OK" || true
cleanup_job "$JAVA_LIB_WORK"
cleanup_job "$JAVA_BUILD"

# ===========================================================================
# 5) 断网验证 + 非 root 验证
# ===========================================================================
say ""
say "== [5/6] 断网验证（--network none）+ 非 root 验证 =="
NET_WORK="$(new_job_dir)"
cat > "$NET_WORK/script/net.sh" <<'NETEOF'
#!/bin/bash
set -u
fail=0
net_inner() {
    local tool="$1"; shift
    if command -v "$tool" >/dev/null 2>&1; then
        if "$tool" "$@" >/dev/null 2>&1; then
            echo "网络访问意外成功: $tool" >&2
            fail=1
        else
            echo "网络访问如预期失败: $tool"
        fi
    else
        echo "镜像内无 $tool（跳过该项检查）"
    fi
}
net_inner curl     -sS --max-time 3 http://example.com/ -o /dev/null
net_inner wget     -q -T 3 http://example.com/ -O /dev/null
net_inner ping     -c 1 -W 3 8.8.8.8
net_inner getent   hosts example.com
echo "SMOKE-nonet-OK"
echo "SMOKE-nonet-OK" > "$RESULT_DIR/out.txt"
[ "$fail" -eq 0 ]
NETEOF
NET_EXIT=0
docker_output="$(run_sandbox_job bash net.sh "$NET_WORK/script" "$NET_WORK/data" "$NET_WORK/result")" || NET_EXIT=$?
say "$docker_output"
assert_result "断网验证（curl/wget/ping/getent 均失败但脚本自身成功）" "$NET_EXIT" "$NET_WORK/result" "SMOKE-nonet-OK" || true
cleanup_job "$NET_WORK"

# 非 root：镜像默认 USER sandboxuser(uid1000) + 服务端 --user 1000:1000
NONROOT_OUTPUT="$(docker run --rm --network none --user 1000:1000 "$IMAGE" id -u 2>&1)" || NONROOT_EXIT=$?
NONROOT_EXIT=${NONROOT_EXIT:-0}
say "容器内 id -u = $NONROOT_OUTPUT（exit=$NONROOT_EXIT）"
if [ "$NONROOT_EXIT" -eq 0 ] && [ "$(printf '%s' "$NONROOT_OUTPUT" | tr -d '\r\n[:space:]')" = "1000" ]; then
    ok "非 root 验证: 容器以 uid 1000 而非 root 运行"
else
    bad "非 root 验证失败: id -u 输出 '$NONROOT_OUTPUT' exit=$NONROOT_EXIT"
fi

# ===========================================================================
# 6) 超时杀验证（可选）—— sleep 60 大于外层 timeout 20，应被杀死
# ===========================================================================
say ""
say "== [6/6] 超时杀验证 =="
if command -v timeout >/dev/null 2>&1; then
    TIMEOUT_WORK="$(new_job_dir)"
    cat > "$TIMEOUT_WORK/script/slow.sh" <<'TIMEEOF'
#!/bin/bash
echo "开始长眠 60s（容器应被外层 timeout 提前杀死）"
sleep 60
echo "SMOKE-timeout-DONE" > "$RESULT_DIR/out.txt"
exit 0
TIMEEOF
    T0="$(date +%s)"
    TIMEOUT_EXIT=0
    timeout 20 docker run --rm --network none --memory 512m --cpus 1 --user 1000:1000 \
        --mount "type=bind,source=$(host_path "$TIMEOUT_WORK/script"),target=/script,readonly" \
        --mount "type=bind,source=$(host_path "$TIMEOUT_WORK/data"),target=/data,readonly" \
        --mount "type=bind,source=$(host_path "$TIMEOUT_WORK/result"),target=/result" \
        -e DATA_DIR=/data -e 'INPUT_FILES=[]' -e RESULT_DIR=/result \
        "$IMAGE" /usr/local/bin/launcher bash /script/slow.sh /data 2>&1 || TIMEOUT_EXIT=$?
    T1="$(date +%s)"
    if [ "$TIMEOUT_EXIT" -ne 0 ] && { [ "$TIMEOUT_EXIT" -eq 124 ] || [ "$TIMEOUT_EXIT" -eq 137 ]; }; then
        ok "超时杀验证: sleep 60 在 $(($T1-$T0))s 内被杀（$TIMEOUT_EXIT），未长期挂起"
    else
        bad "超时杀验证: 异常退出码=$TIMEOUT_EXIT（预期 124/137）"
    fi
    [ -f "$TIMEOUT_WORK/result/out.txt" ] && bad "超时杀验证: 慢脚本居然写完了 out.txt（未被正确杀死）"
    cleanup_job "$TIMEOUT_WORK"
else
    say "（跳过）当前 shell 无 coreutils timeout，无法做超时杀验证（Linux/WSL2 下有）"
fi

# ===========================================================================
say ""
if [ "$FAIL" -eq 0 ]; then
    say "冒烟结果: 全部通过（$PASS 项 PASS）—— $IMAGE 交付可用"
    exit 0
else
    say "冒烟结果: $PASS 项 PASS / $FAIL 项 FAIL —— 请按失败项排查"
    exit 1
fi
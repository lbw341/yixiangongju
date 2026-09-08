#!/bin/bash
# =============================================================================
# 容器内 launcher —— Docker 全限制沙箱的统一脚本分发器
#
# 约定（与 SandboxExecutionService.buildDockerRunArgs 严格一致）：
#   调用来历：docker run ... <image> /usr/local/bin/launcher <runtime> <script> <dataDir> [files...]
#   环境注入（经 -e）：DATA_DIR=/data、INPUT_FILES=<JSON 数组>、RESULT_DIR=/result
#
#   参数：
#     $1 runtime   —— python | node | bash | java | bat | cmd
#     $2 script    —— 容器内脚本路径，固定为 /script/<scriptName>（只读挂载）
#     $3 dataDir   —— 容器内数据目录，固定为 /data（只读挂载）
#     $4... files  —— 容器内数据文件路径列表（/data/...），0..N 个
#
# 目标：与宿主 ToolRunner 的命令形态对齐，让工具作者的脚本拿到统一 I/O 契约：
#   - 脚本 argv[1] = dataDir，argv[2:] = 数据文件列表
#   - 环境变量 DATA_DIR / INPUT_FILES / RESULT_DIR 均可用（结果写到 RESULT_DIR）
#
# 已知限制（详见 docs/部署与沙箱运维.md）：
#   - python venv 暂未挂载进容器（宿主 pip 预装；容器内仅系统 python3）
#   - java lib/ 依赖目录暂未挂载进容器（SandboxExecutionService 只挂 script/ 单文件）
#     → 此脚本保留 lib/ 解析逻辑（读取入口 jar 清单 Main-Class 后走 -cp），
#       待后续版本把工具包目录挂进容器后自动生效；现在容器内没有 lib/ 则直接 -jar。
#   - bat/cmd 为 Windows 运行时，本 Linux 容器内无法执行——直接报错退出。
# =============================================================================

runtime="$1"
script="$2"
dataDir="$3"
shift 3

if [ -z "$runtime" ] || [ -z "$script" ] || [ -z "$dataDir" ]; then
    echo "用法错误: launcher <runtime> <script> <dataDir> [files...]" >&2
    echo "当前收到的参数: runtime='${runtime:-<空>}' script='${script:-<空>}' dataDir='${dataDir:-<空>}'" >&2
    exit 1
fi

# 统一 I/O 契约自检：三个环境变量在 -e 注入下应始终存在
: "${DATA_DIR:?缺少环境变量 DATA_DIR}"
: "${RESULT_DIR:?缺少环境变量 RESULT_DIR}"
if [ -n "${INPUT_FILES-}" ]; then
    echo "[launcher] INPUT_FILES=${INPUT_FILES}"
else
    echo "[launcher] INPUT_FILES=(空)"
fi

if [ ! -r "$script" ]; then
    echo "错误: 脚本不可读: $script" >&2
    exit 1
fi

case "$runtime" in
    python)
        # ToolRunner I/O 契约: argv[1]=dataDir, argv[2:]=files
        exec python3 -u "$script" "$dataDir" "$@"
        ;;
    node)
        # NodeRunner 契约一致
        exec node "$script" "$dataDir" "$@"
        ;;
    bash)
        # BashRunner 契约一致
        exec bash "$script" "$dataDir" "$@"
        ;;
    java)
        # JavaRunner 契约: 存在 lib/ 时读入口 jar 清单 Main-Class，按
        # -cp <jar>:<lib>/* <MainClass> 启动（与 JavaRunner.readMainClass 同语义），
        # 否则 -jar 启动；均传 argv[1]=dataDir。
        script_dir="$(dirname "$script")"
        if [ -d "$script_dir/lib" ]; then
            main_class=""
            if command -v unzip >/dev/null 2>&1; then
                main_class=$(unzip -p "$script" META-INF/MANIFEST.MF 2>/dev/null \
                    | sed -n 's/^Main-Class:[[:space:]]*\(.*\)/\1/p' \
                    | sed 's/[[:space:]]*$//' | tr -d '\r')
            elif command -v jar >/dev/null 2>&1; then
                manifest_tmp="$(mktemp -d)"
                if (cd "$manifest_tmp" && jar xf "$script" META-INF/MANIFEST.MF) >/dev/null 2>&1 \
                    && [ -f "$manifest_tmp/META-INF/MANIFEST.MF" ]; then
                    main_class=$(sed -n 's/^Main-Class:[[:space:]]*\(.*\)/\1/p' \
                        "$manifest_tmp/META-INF/MANIFEST.MF" \
                        | sed 's/[[:space:]]*$//' | tr -d '\r')
                fi
                rm -rf "$manifest_tmp"
            fi
            if [ -n "$main_class" ]; then
                echo "[launcher] java -cp \"$script:$script_dir/lib/*\" $main_class 运行中..."
                exec java -cp "$script:$script_dir/lib/*" "$main_class" "$dataDir" "$@"
            fi
            # 无法解析 Main-Class 时退回 -jar
        fi
        echo "[launcher] java -jar \"$script\" 运行中..."
        exec java -jar "$script" "$dataDir" "$@"
        ;;
    bat|cmd)
        # CONSTRAINT: bat 是 Windows 专属运行时，本 Linux 沙箱容器无 cmd.exe
        echo "bat 运行时在 Linux 沙箱容器内不受支持（无 cmd.exe）；仅支持进程级回退或 Windows 容器"
        exit 1
        ;;
    *)
        echo "错误: 不支持的 runtime '$runtime'（支持: python/node/bash/java）" >&2
        exit 1
        ;;
esac
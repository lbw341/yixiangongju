# =============================================================================
# tool-sandbox —— Docker 全限制沙箱镜像
#
# 内含运行沙箱脚本所需的四类运行时：bash + python3 + node + jdk（temurin）。
# 镜像构建发生在部署方机器上（docker build 需联网拉基础镜像 + apt 源），
# 与沙箱运行期 `--network none` 完全解耦——沙箱内无任何网络。
#
# 已知限制（见 docs/部署与沙箱运维.md）：
#   - python venv 尚未挂载进容器：脚本作者在宿主上传/制作 venv（ScriptPackageService
#     的 venv 目录 uploads/venvs），但 SandboxExecutionService 目前不把 venv 挂进容器，
#     容器内只能用系统 python3（无宿主预装 pip 包）。
#   - java lib/ 依赖目录尚未挂载进容器：入口 jar 旁的 lib/*.jar 目录未随 script 挂载，
#     容器内仅存在入口 jar 单文件（/script/<name>.jar），lib/ 解析要在后续版本补充。
# =============================================================================

FROM eclipse-temurin:23-jdk

# 装运行时：temurin 镜像自带 bash 与 curl；补 python3（默认解释器为 python3 而非 python）
# 与 nodejs。--no-install-recommends 与清缓存控制镜像体积。
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        python3 \
        python3-pip \
        nodejs \
    && rm -rf /var/lib/apt/lists/*

# 创建非 root 沙箱运行用户（SandboxExecutionService 以 --user 1000:1000 启动容器）。
# 绑定挂载要求：/data、/script 只读（已有物何谓可读由挂载 readonly 保证），
# /result 须 uid 1000 可写——容器默认入口即此用户，无需额外 chown。
RUN groupadd --gid 1000 sandboxuser \
    && useradd --uid 1000 --gid 1000 --create-home --home-dir /tmp/sandboxuser --shell /bin/bash sandboxuser

# 拷贝容器 launcher 并设可执行位（SandboxExecutionService.launcher-container-path 默认
# /usr/local/bin/launcher，application.properties 的 sandbox.launcher-container-path 一致）。
COPY deploy/launcher.sh /usr/local/bin/launcher
RUN chmod +x /usr/local/bin/launcher

# 工作目录：非 root 用户可直接读写；HOME 指向 /tmp/sandboxuser 避免写宿主挂载目录。
WORKDIR /app
ENV HOME=/tmp/sandboxuser

# 默认以非 root 运行；SandboxExecutionService 仍会显式传 --user 1000:1000。
USER sandboxuser

CMD ["/usr/local/bin/launcher"]
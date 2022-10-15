FROM gitpod/workspace-mysql

USER gitpod

# Install some tooling need to develop
# - SDKMAN https://sdkman.io
# - Eclipse Temurin https://adoptium.net/es/
# - Task https://taskfile.dev/
# - Mockintosh https://mockintosh.io/
# - pipx https://pypa.github.io/pipx/
# ====== Java
RUN bash -c ". /home/gitpod/.sdkman/bin/sdkman-init.sh && \
    sdk install java 17.0.3-tem && \
    sdk default java 17.0.3-tem"
# ====== Go
RUN bash -c "brew install go-task/tap/go-task"

# ====== Python
RUN bash -c "python3 -m pip install --user pipx && \
    python3 -m pipx ensurepath --force"
COPY ./__scripts__/custom_init.sh /workspace/custom_init.sh
RUN chmod +x /workspace/custom_init.sh
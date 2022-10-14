FROM gitpod/workspace-mysql

USER gitpod

# Install some tooling need to develop
# - SDKMAN https://sdkman.io
# - Eclipse Temurin https://adoptium.net/es/
# - Task https://taskfile.dev/
# - Mockintosh https://mockintosh.io/
# - pipx https://pypa.github.io/pipx/
RUN bash -c ". /home/gitpod/.sdkman/bin/sdkman-init.sh && \
    sdk install java 17.0.3-tem && \
    sdk default java 17.0.3-tem && \
    brew install go-task/tap/go-task && \
    python3 -m pip install --user pipx && \
    python3 -m pipx ensurepath && \
    pipx install --user mockintosh"

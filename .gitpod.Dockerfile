FROM gitpod/workspace-mysql

USER gitpod

RUN bash -c ". /home/gitpod/.sdkman/bin/sdkman-init.sh && \
    sdk install java 17.0.3-ms && \
    sdk default java 17.0.3-ms && \
    brew install go-task/tap/go-task && \
    brew install up9inc/repo/mockintosh"

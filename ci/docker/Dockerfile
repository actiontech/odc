# Use Ubuntu 22.04 for better compatibility and mirror support
FROM ubuntu:22.04

# Use Aliyun mirror for Ubuntu
RUN sed -i 's/archive.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list && \
    sed -i 's/security.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
    openjdk-8-jdk \
    rpm \
    file \
    git \
    curl \
    zip \
    unzip \
    make \
    gcc \
    g++ \
    xz-utils \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Set up environment variables
ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
ENV MAVEN_HOME=/usr/local/maven
ENV NODE_VERSION=18.20.0
ENV PATH=$MAVEN_HOME/bin:$PATH
ENV CI=true

# Install Maven 3.6.3 (using Huawei Cloud mirror for speed)
RUN curl -fsSL https://repo.huaweicloud.com/apache/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.tar.gz | tar xzf - -C /usr/local \
    && ln -s /usr/local/apache-maven-3.6.3 /usr/local/maven

# Install Node.js (using domestic mirror)
RUN curl -fsSL https://mirrors.tuna.tsinghua.edu.cn/nodejs-release/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz | tar xJf - -C /usr/local --strip-components=1 \
    && ln -s /usr/local/bin/node /usr/bin/node \
    && ln -s /usr/local/bin/npm /usr/bin/npm

# Install pnpm 10.28.1 (as requested/latest)
RUN npm config set registry https://registry.npmmirror.com && \
    npm install -g pnpm@10.28.1 && \
    pnpm config set update-notifier false && \
    pnpm config set registry https://registry.npmmirror.com

# Configure Maven to use Aliyun mirror
RUN mkdir -p /root/.m2 && \
    echo '<?xml version="1.0" encoding="UTF-8"?><settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd"><mirrors><mirror><id>aliyunmaven</id><mirrorOf>*</mirrorOf><name>阿里云公共仓库</name><url>https://maven.aliyun.com/repository/public</url></mirror></mirrors></settings>' > /root/.m2/settings.xml

# Create build directory
WORKDIR /root/odc

# Default entrypoint (can be overridden)
CMD ["/bin/bash"]

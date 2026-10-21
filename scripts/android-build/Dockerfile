# Android build + publish image for ZimaOS (scripts/apk_publish.sh).
# Signing key is NOT in the image: mounted from /DATA/AppData/netmovies-signing.
FROM eclipse-temurin:17-jdk-noble
RUN apt-get update && apt-get install -y --no-install-recommends unzip curl git python3 ca-certificates \
 && rm -rf /var/lib/apt/lists/*
ENV ANDROID_HOME=/opt/android-sdk
RUN curl -fsSL -o /tmp/t.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip \
 && mkdir -p $ANDROID_HOME/cmdline-tools && unzip -q /tmp/t.zip -d $ANDROID_HOME/cmdline-tools \
 && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest && rm /tmp/t.zip \
 && yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null \
 && $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-36" "build-tools;36.0.0" "build-tools;35.0.0" "platform-tools" >/dev/null \
 && chmod -R a+rX $ANDROID_HOME
RUN curl -fsSL https://github.com/cli/cli/releases/download/v2.63.2/gh_2.63.2_linux_amd64.tar.gz \
  | tar -xz -C /tmp && mv /tmp/gh_2.63.2_linux_amd64/bin/gh /usr/local/bin/ && rm -rf /tmp/gh_*
ENV PATH=$PATH:$ANDROID_HOME/build-tools/36.0.0:$ANDROID_HOME/platform-tools

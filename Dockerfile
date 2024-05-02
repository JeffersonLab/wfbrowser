ARG BUILD_IMAGE=gradle:7.4-jdk17
#ARG RUN_IMAGE=jeffersonlab/wildfly:1.4.0
ARG RUN_IMAGE=wildfly-test
ARG CUSTOM_CRT_URL=http://pki.jlab.org/JLabCA.crt

############ Stage 0
FROM ${BUILD_IMAGE} as builder
ARG CUSTOM_CRT_URL
USER root
WORKDIR /
RUN if [ -z "${CUSTOM_CRT_URL}" ] ; then echo "No custom cert needed"; else \
           wget -O /usr/local/share/ca-certificates/customcert.crt $CUSTOM_CRT_URL \
           && update-ca-certificates \
           && keytool -import -alias custom -file /usr/local/share/ca-certificates/customcert.crt -cacerts -storepass changeit -noprompt \
           && export OPTIONAL_CERT_ARG=--cert=/etc/ssl/certs/ca-certificates.crt \
    ; fi
COPY . /app
RUN cd /app && gradle build -x test --no-watch-fs $OPTIONAL_CERT_ARG


############ Stage 1
FROM ${RUN_IMAGE} as runner
COPY --from=builder /app/docker/server/server-setup.env /
COPY --from=builder /app/docker/app/app-setup.env /
USER root:root
# The app relies on the server's localtime to be the same as the timezone where the data was taken (Eastern or NewYork)
# since the database stores them in UTC, but the disk stores them in localtime for the server that wrote the data.
RUN chsh -s /bin/bash jboss \
    && /server-setup.sh /server-setup.env wildfly_start_and_wait \
    && /server-setup.sh /server-setup.env config_email \
    && ln -sf ../usr/share/zoneinfo/US/Eastern /etc/localtime
RUN /app-setup.sh /app-setup.env
USER jboss:jboss
COPY --from=builder /app/build/libs/* /opt/jboss/wildfly/standalone/deployments
#!/bin/bash

echo "-----------------------------"
echo "| Step I: Create App Roles  |"
echo "-----------------------------"

# The are the roles that the app-based security is expecting a privileged user to have
${KEYCLOAK_HOME}/bin/kcadm.sh create roles -r "${KEYCLOAK_REALM}" -s name=${WFB_ADMIN_ROLE}
${KEYCLOAK_HOME}/bin/kcadm.sh create roles -r "${KEYCLOAK_REALM}" -s name=${WFB_POST_ROLE}


echo "-----------------------------"
echo "| Step II: Create Users     |"
echo "-----------------------------"
# This setup is a regular user with no special privileges
${KEYCLOAK_HOME}/bin/kcadm.sh create users -r "${KEYCLOAK_REALM}" -s username=user1 -s firstName=James -s lastName=Johnson -s email=user1@example.com -s enabled=true
${KEYCLOAK_HOME}/bin/kcadm.sh set-password -r "${KEYCLOAK_REALM}" --username user1 --new-password password
${KEYCLOAK_HOME}/bin/kcadm.sh add-roles -r "${KEYCLOAK_REALM}" --uusername user1 --rolename ${KEYCLOAK_RESOURCE}-user

# This setup is for a real human admin
${KEYCLOAK_HOME}/bin/kcadm.sh create users -r "${KEYCLOAK_REALM}" -s username=dev1 -s firstName=Software -s lastName=Developer -s email=dev1@example.com -s enabled=true
${KEYCLOAK_HOME}/bin/kcadm.sh set-password -r "${KEYCLOAK_REALM}" --username dev1 --new-password password
${KEYCLOAK_HOME}/bin/kcadm.sh add-roles -r "${KEYCLOAK_REALM}" --uusername dev1 --rolename ${WFB_ADMIN_ROLE}

# Create a service account for doing automated data posting from scripts, etc.
${KEYCLOAK_HOME}/bin/kcadm.sh create clients -r "${KEYCLOAK_REALM}" -s clientId=${WFBADM_NAME} -s 'redirectUris=["https://localhost:8443/'${KEYCLOAK_RESOURCE}'/*"]' -s secret=${WFBADM_SECRET} -s 'serviceAccountsEnabled=true'
${KEYCLOAK_HOME}/bin/kcadm.sh add-roles -r "${KEYCLOAK_REALM}"  --uusername service-account-${WFBADM_NAME} --rolename ${WFB_POST_ROLE}


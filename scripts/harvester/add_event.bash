#!/bin/bash

# Version history
# v1.0 2018-xx-xx Initial version to update test server
# v1.1 Updated to include classification and group event flags
# v1.1.1
# v1.2 Updated to include event labels (e.g., machine learning labels for faults)
#        and authenticate against keycloak
# v1.3 Updated to support RHEL 9 only.  Performs explicit checks for commands

# Command Definitions (RHEL9 Only)
CURL='/bin/curl'
GREP='/bin/grep'
MAILX='/bin/mailx'
DIRNAME='/usr/bin/dirname'
JQ='/bin/jq'
CAT='/bin/cat'
# These are checked by validate_commands
COMMANDS="$CURL $GREP $MAILX $DIRNAME $JQ $CAT"

SCRIPT_DIR=$($DIRNAME "$0")

# Which version of the script is this.  Needed to comply with certified rules
SCRIPT_VERSION='v1.3'

# Who to notifiy in case of error
#EMAIL_ADDRESS='accharvester@jlab.org'
EMAIL_ADDRESS='adamc@jlab.org'

# CURL parameters
curl_config="${SCRIPT_DIR}/../../cfg/add_event1.0.cfg"

##################################
# Server configuration blocks.
##################################
# SERVER - what we post events to
# KEYlCOAK_SERVER - the keycloak server we are authenticating against (often a reverse proxy)
# CLIENT_ID - keycloak client that we are authenticating against

# PRODUCTION
#SERVER="accweb.acc.jlab.org"
#KEYCLOAK_SERVER="accweb.acc.jlab.org"
#CLIENT_ID="accweb-auth-util"

# TESTING
SERVER="accwebtest.acc.jlab.org"
KEYCLOAK_SERVER="accwebtest.acc.jlab.org"
CLIENT_ID="accwebtest-auth-util"

# DEVELOPMENT
#SERVER="sftadamc2.acc.jlab.org:8181"
#KEYCLOAK_SERVER="accwebtest.acc.jlab.org"
#CLIENT_ID="accwebtest-auth-util"
##################################

usage () {
    cat - <<EOF
Usage: $0 [-h] <-s <system>> <-l <location>> <-c <classification>>
          <-t <date_time>> <-g <is_grouped>> <-f <filename>> [-L <label_json>]
-h                   Show this help message
-s <system>          System name. Ex. rf
-l <location>        Location name.  Ex. 1L22
-c <classification>  Classification. Ex. periodic
-t <date_time>       Event timestamp. Ex. "2018-12-01 15:30:05.1"
-g <is_grouped>      Is event a group of capture files (true/false)
-f <file_name>       The name of the capture file to import
-L <label_json>      JSON string describing a label.  (Repeatable)

This script POSTs to the wfbrowser web service to request the addition
of an event specified by the above arguments.  The data should be on
the filesystem in the location dictated by the given parameters. A
server can have a different root directory path, and grouped and 
events will likely have different formats.

The -L option accepts a json formatted string that will be sent to
wfbrowser unchanged.  Multiple labels can be specified by repeating
the -L option.

Giving no options will cause this script to produce a version number.
EOF
}

# Check that the required commands exist
validate_commands () {
  for cmd in $COMMANDS
  do
    if [ ! -x "$cmd" ] ; then
      echo "Required executable not found '$cmd'"
      return 1
    fi
  done
  return 0
}

# The directory contain this script

# Authenticate against a keycloak server and get the needed auth TOKEN for
# web access.
# client_id - The KeyCloak id of the service against which we are going to authenticate.
#             This client must allow public authentication, like accweb-auth-util does.
# keycloak_host - the hostname of the KeyCloak server
#
# Returns the authentation "bearer" token.
# Note: Set the following header to use it - "Authorization: bearer <TOKEN>"
do_keycloak_auth () {
    client_id="$1"
    keycloak_host="$2"
    realm="jlab"

    # curl_config should contain username and password as -d options
    result=$($CURL -s \
        -d "grant_type=password" \
        -d "client_id=${client_id}" \
        -K $curl_config \
        https://${keycloak_host}/auth/realms/${realm}/protocol/openid-connect/token 2>&1)
    exit_val=$?

    # A problem making the request will give non-zero exit_val
    if [ "$exit_val" != "0" ] ; then
        echo $result
        return $exit_val
    fi
    
    # A failed auth attempt will return exit_val == 0, but have a null access_token.  Instead you get
    # error and error_description fields.  Print the response and return 1 for caller to handle.
    token=$(echo $result | $JQ -r '.access_token')
    if [ "null" == "$token" -o -z "$token" ] ; then
        echo $result 
        return 1
    fi

    echo $token
    return $exit_val
}

# Simple function for sending out a standard notification
alert () {
    message="$1"
    server="$2"
    system="$3"
    location="$4"
    classification="$5"
    timestamp="$6"
    grouped="$7"
    file="$8"

    # Grab however many labels are left over
    shift 8
    labels="$@"

    mail_body="${message}\n\n"
    mail_body="${mail_body}Server: $server\n"
    mail_body="${mail_body}System: $system\n"
    mail_body="${mail_body}Location: $location\n"
    mail_body="${mail_body}Classification: $classification\n"
    mail_body="${mail_body}Timestamp: $timestamp\n"
    mail_body="${mail_body}Grouped: $grouped\n"
    mail_body="${mail_body}File: $file\n"
    mail_body="${mail_body}Labels: $labels\n"

    # Print out the message for the harvester log
    echo "$message server=$server system=$system location=$location classification=$classification timestamp=$timestamp grouped=$grouped file=$file labels=$labels"

    # Email out the more verbose message to the concerned parties
    echo -e $mail_body | $MAILX -s "[Waveform Harvester Error] wfbrowser data import failed" $EMAIL_ADDRESS
}

# This function adds an event to the waveform browser server using an HTTP endpoint.
# The HTTP endpoint requires an authorized user in a role that has permissions to
# POST to the event HTTP endpoint (ADMIN, EVENTPOST roles as of Nov 2018).
add_event_to_server () {

    # Get all of the required options
    server=$1
    system=$2
    location=$3
    classification=$4
    timestamp=$5
    grouped=$6
    file=$7
    
    # We have a variable number of labels from 0 to N.  Shift off the first seven, then read
    # the leftovers into an array for use later.
    shift 7
    labels=("$@")


    # URL pieces for making requests
    login_url="https://${server}/wfbrowser/login"
    event_url="https://${server}/wfbrowser/ajax/event"

    # Build up the label parameters as needed.  It seems that multiple -d's with the same
    # parameter name overwrite each other.
    label_data=""
    for label in "${labels[@]}"
    do
        if [ -z $label_data ] ; then
            label_data="label=${label}"
        else
            label_data="$label_data&label=${label}"
        fi
    done

    # Authenticate against the keycloak server.  This should return either the needed authorization
    # token, or the error response from the attempt if exit_val != 0
    token=$(do_keycloak_auth $CLIENT_ID $KEYCLOAK_SERVER)
    exit_val=$?

    if [ "$exit_val" -ne 0 ] ; then
        # Here $token will contain either the curl error message or the keycloak json response
        msg="Error: received non-zero status=$exit_val from curl login attempt.$\n${token}"
        alert "$msg" "$server" "$system" "$location" "$classification" "$timestamp" "$grouped" \
                "$file" "${labels[@]}"

        return 1
    fi

    # Post the event to the web service
    msg=$($CURL -s -X POST \
            -H "Authorization: bearer $token" \
            -d datetime="$timestamp" \
            -d location="$location" \
            -d system="$system" \
            -d classification="$classification" \
            -d grouped="$grouped" \
            -d captureFile="$file" \
            -d "$label_data" \
            "$event_url")
    exit_val=$?

    # Look for the success message and alert if not found
    match=`echo -e "$msg" | $GREP --count "successfully added"`
    if [ $exit_val -ne 0 -o "$match" -eq 0 ] ; then
         mail_msg="Error:  Problem posting event to webservice.  Response: $msg"
         alert "$mail_msg" "$server" "$system" "$location" "$classification" "$timestamp" "$grouped" \
            "$file" "${labels[@]}"

         return 1
    fi
    
    return 0
}

##### PROCESS ARGUMENTS #####
if [ $# -eq 0 ] ; then
    echo "add_event.bash $SCRIPT_VERSION"
    exit 0
fi

# Xundef used as CLASSIFICATION can be ""
SYSTEM="Xundef"
LOCATION="Xundef"
CLASSIFICATION="Xundef"
TIMESTAMP="Xundef"
GROUPED="Xundef"
FILE="Xundef"
# NOTE: LABELS is an array.
LABELS=()

while getopts "s:l:c:t:g:f:L:" opt; do
    case $opt in
        h) usage
           exit 0
           ;;
        s) SYSTEM="$OPTARG"
           ;;
        l) LOCATION="$OPTARG"
           ;;
        c) CLASSIFICATION="$OPTARG"
           ;;
        t) TIMESTAMP="$OPTARG"
           ;;
        g) GROUPED="$OPTARG"
           ;;
        f) FILE="$OPTARG"
           ;;
        L) LABELS+=("$OPTARG")
           ;;
       \?) echo "Unknown Option: $opt"
           usage
           exit 1
           ;;
        *) echo "Unknown Option: $opt"
           usage
           exit 1
           ;;
    esac
done

# The expression $(($OPTIND - 1)) is an arithmetic expression equal to $OPTIND minus 1.
# This value is used as the argument to shift. The result is that the correct number of
# arguments are shifted out of the way, leaving the real arguments as $1, $2, etc.
shift $(($OPTIND - 1))

# Make sure we don't have any extra options left over
if [ $# -ne 0 ] ; then
    usage
    exit 1
fi

# Make sure everything was set that needs to be.  Xundef used as CLASSIFICATION can be ""
if [ "$SYSTEM" == "Xundef" ] ; then
    echo "-s <system> required"
    usage
    exit 1
fi
if [ "$LOCATION" == "Xundef" ] ; then
    echo "-l <location> required"
    usage
    exit 1
fi
if [ "$CLASSIFICATION" == "Xundef" ] ; then
    echo "-c <classification> required"
    usage
    exit 1
fi
if [ "$TIMESTAMP" == "Xundef" ] ; then
    echo "-t <date_time> required"
    usage
    exit 1
fi
if [ "$GROUPED" == "Xundef" ] ; then
    echo "-g <is_grouped> required"
    usage
    exit 1
fi
if [ "$FILE" == "Xundef" ] ; then
    echo "-f <file> required"
    usage
    exit 1
fi

###### MAIN ROUTINE #####
msg=$(validate_commands)
if [ $? -eq 1 ] ; then
    alert "$msg" "$SERVER" "$SYSTEM" "$LOCATION" "$CLASSIFICATION" "$TIMESTAMP" "$GROUPED" "$FILE" \
              "${LABELS[@]}"
    exit 1
fi


if [ ! -r $curl_config ] ; then
    msg="Error: $curl_config does not exist or is not readable.  Unable to add event to service."
    alert "$msg" "$SERVER" "$SYSTEM" "$LOCATION" "$CLASSIFICATION" "$TIMESTAMP" "$GROUPED" "$FILE" \
          "${LABELS[@]}"
    exit 1
fi

add_event_to_server "$SERVER" "$SYSTEM" "$LOCATION" "$CLASSIFICATION" "$TIMESTAMP" "$GROUPED" "$FILE" \
    "${LABELS[@]}"
exit $?

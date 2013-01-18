#!/bin/sh

#
# $Id$
#

# DEST_DIR=/home/mcidasv/idv/lib
DEST_DIR=../../IDV/lib
URL=ftp://ftp.ssec.wisc.edu/pub/visad/nightly
FILE=visad.jar

export OLDPWD=${PWD}
mkdir -p ${DEST_DIR}
cd ${DEST_DIR}
wget ${URL}/${FILE} -O ${FILE}
cd ${OLDPWD}

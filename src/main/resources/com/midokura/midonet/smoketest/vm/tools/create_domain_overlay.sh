#!/bin/bash

BASE_IMAGE=$1
MACHINE_NAME=$2

echo Creating overlay image for hostname \"${MACHINE_NAME}\" based on image: \"${BASE_IMAGE}\"

qemu-img create -b "${BASE_IMAGE}" -f qcow2 ${MACHINE_NAME}_image.ovl

QEMU_NBD_PIDS=`pidof qemu-nbd`
if [ "x${QEMU_NBD_PIDS}x" != "xx" ]; then
	echo ${QEMU_NBD_PIDS} | xargs kill -9
fi

qemu-nbd --connect=/dev/nbd0 ${MACHINE_NAME}_image.ovl

stat /dev/nbd0p1 2>&1 1>/dev/null
while [ $? -ne 0 ]; do
	echo "Waiting for the ndb client to read the partitions"
	sleep 1;
	stat /dev/nbd0p1 2>&1 1>/dev/null
done

mkdir -p mnt/image_${MACHINE_NAME}
mount /dev/nbd0p1 mnt/image_${MACHINE_NAME}

HOSTNAME=`cat mnt/image_${MACHINE_NAME}/etc/hostname`
echo "Found machine hostname to be: ${HOSTNAME}"
echo "Changing it to: ${MACHINE_NAME}"


echo ${MACHINE_NAME} > mnt/image_${MACHINE_NAME}/etc/hostname
sed -i.bak -e "s/^\([0-9\.]\+\) ${HOSTNAME}\.\([^ ]\+\) ${HOSTNAME}$/\1 ${MACHINE_NAME}.\2 ${MACHINE_NAME}/g" mnt/image_${MACHINE_NAME}/etc/hosts

umount -l mnt/image_${MACHINE_NAME}
qemu-nbd --disconnect ${MACHINE_NAME}_image.ovl

QEMU_NBD_PIDS=`pidof qemu-nbd`
if [ "x${QEMU_NBD_PIDS}x" != "xx" ]; then
	echo ${QEMU_NBD_PIDS} | xargs kill -9
fi

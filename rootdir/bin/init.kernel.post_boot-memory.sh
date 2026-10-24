#=============================================================================
# Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
# All rights reserved.
# Confidential and Proprietary - Qualcomm Technologies, Inc. 
#=============================================================================

enable_thp()
{
	# THP enablement settings
	ProductName=`getprop ro.product.name`
	if [ "$ProductName" == "muyu" ] || [ "$ProductName" == "ruyi" ] || [ "$ProductName" == "zorn" ]; then
		echo never > /sys/kernel/mm/transparent_hugepage/enabled
	else
		echo always > /sys/kernel/mm/transparent_hugepage/enabled
	fi

	MemTotalStr=`cat /proc/meminfo | grep MemTotal`
	MemTotal=${MemTotalStr:16:8}
	let RamSizeGB="( $MemTotal / 1048576 ) + 1"

	# Set the min_free_kbytes to standard kernel value
	if [ $RamSizeGB -ge 8 ]; then
		MinFreeKbytes=11584
	elif [ $RamSizeGB -ge 4 ]; then
		MinFreeKbytes=8192
	elif [ $RamSizeGB -ge 2 ]; then
		MinFreeKbytes=5792
	else
		MinFreeKbytes=4096
	fi

	# We store min_free_kbytes into a vendor property so that the PASR
	# HAL can read and set the value for it.
	echo $MinFreeKbytes > /proc/sys/vm/min_free_kbytes
	setprop vendor.memory.min_free_kbytes $MinFreeKbytes

	#Enable the PASR support
	ddr_type=`od -An -tx /proc/device-tree/memory/ddr_device_type`
	ddr_type5="08"

	if [ -d /sys/kernel/mem-offline ]; then
		#only LPDDR5 supports PAAR
		if [ ${ddr_type:4:2} != $ddr_type5 ]; then
			setprop vendor.pasr.activemode.enabled false
		fi
		setprop vendor.pasr.enabled true
	fi
}

case "$2" in
	"632"|"643")
		#THP is disabled by default. Do nothing here.
		;;
	*)
		enable_thp
		;;
esac

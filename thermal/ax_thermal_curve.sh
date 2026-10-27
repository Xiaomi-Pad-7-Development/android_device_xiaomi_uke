#!/vendor/bin/sh
#
# SPDX-FileCopyrightText: 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#
# User-defined throttling curve, used instead of mi_thermald.
# The board temperature is the virtual sensor of the stock mi_thermald config (this formula matches the live
# thermal_message/board_sensor_temp of the Pad within 0.3 degC; zones: battery quiet_therm charger_therm0 wifi_therm cpu_therm).
# Curves: persist.sys.ax.thermal_curve.{cpu,gpu} = "temp:percent,..." (degC, %),
# sorted by temperature. Usage: ax_thermal_curve.sh [restore]

DEFAULT_CURVE="40:100,45:100,50:90,55:75,60:55"
INTERVAL=2
MIN_PERCENT=10
GPU=/sys/class/kgsl/kgsl-3d0

zone() {
    for z in /sys/class/thermal/thermal_zone*; do
        if [ "$(cat $z/type 2>/dev/null)" = "$1" ]; then
            echo $z/temp
            return
        fi
    done
}

read_temp() {
    v=$(cat $1 2>/dev/null)
    echo ${v:-0}
}

BATT=$(zone battery)
QUIET=$(zone quiet_therm)
CHG=$(zone charger_therm0)
WIFI=$(zone wifi_therm)
CPU=$(zone cpu_therm)

# Board temperature in millidegrees
board_temp() {
    if [ -z "$BATT" ] || [ -z "$QUIET" ] || [ -z "$CHG" ] || [ -z "$WIFI" ]; then
        read_temp $CPU
        return
    fi
    echo $(( (407 * $(read_temp $BATT) + 101 * $(read_temp $QUIET) + 90 * $(read_temp $CHG) \
              - 44 * $(read_temp $WIFI) + 383 * $(read_temp $CPU)) / 1000 + 1312 ))
}

# $1 - curve, $2 - temperature in millidegrees
curve_percent() {
    t=$2
    prev_t=
    prev_p=100
    for point in $(echo $1 | tr ',' ' '); do
        pt=$(( ${point%%:*} * 1000 ))
        pp=${point##*:}
        if [ $t -le $pt ]; then
            if [ -z "$prev_t" ]; then
                prev_p=$pp
            else
                prev_p=$(( prev_p + (pp - prev_p) * (t - prev_t) / (pt - prev_t) ))
            fi
            prev_t=done
            break
        fi
        prev_t=$pt
        prev_p=$pp
    done
    [ $prev_p -lt $MIN_PERCENT ] && prev_p=$MIN_PERCENT
    [ $prev_p -gt 100 ] && prev_p=100
    echo $prev_p
}

apply_cpu() {
    for p in /sys/devices/system/cpu/cpufreq/policy*; do
        target=$(( $(cat $p/cpuinfo_max_freq) * $1 / 100 ))
        best=$(cat $p/cpuinfo_min_freq)
        for f in $(cat $p/scaling_available_frequencies); do
            [ $f -le $target ] && [ $f -gt $best ] && best=$f
        done
        [ "$(cat $p/scaling_max_freq)" != "$best" ] && echo $best > $p/scaling_max_freq
    done
}

apply_gpu() {
    max=0
    min=
    for f in $(cat $GPU/gpu_available_frequencies); do
        [ $f -gt $max ] && max=$f
        { [ -z "$min" ] || [ $f -lt $min ]; } && min=$f
    done
    [ $max -eq 0 ] && return
    target=$(( max / 100 * $1 ))
    best=$min
    for f in $(cat $GPU/gpu_available_frequencies); do
        [ $f -le $target ] && [ $f -gt $best ] && best=$f
    done
    echo $best > $GPU/max_gpuclk
}

# Drop the limits mi_thermald left behind
reset_cooling() {
    for d in /sys/class/thermal/cooling_device*; do
        case "$(cat $d/type 2>/dev/null)" in
            cpu-cluster*|gpu|cdsp|battery) echo 0 > $d/cur_state ;;
        esac
    done
}

if [ "$1" = "restore" ]; then
    apply_cpu 100
    apply_gpu 100
    exit 0
fi

reset_cooling
last_cpu=
last_gpu=
count=0
while true; do
    cpu_curve=$(getprop persist.sys.ax.thermal_curve.cpu)
    gpu_curve=$(getprop persist.sys.ax.thermal_curve.gpu)
    t=$(board_temp)
    cpu_p=$(curve_percent "${cpu_curve:-$DEFAULT_CURVE}" $t)
    gpu_p=$(curve_percent "${gpu_curve:-$DEFAULT_CURVE}" $t)
    # Re-apply now and then in case something else touched the limits
    count=$(( (count + 1) % 15 ))
    if [ "$cpu_p" != "$last_cpu" ] || [ $count -eq 0 ]; then
        apply_cpu $cpu_p
        last_cpu=$cpu_p
    fi
    if [ "$gpu_p" != "$last_gpu" ] || [ $count -eq 0 ]; then
        apply_gpu $gpu_p
        last_gpu=$gpu_p
    fi
    sleep $INTERVAL
done

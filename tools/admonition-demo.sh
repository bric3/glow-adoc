#!/usr/bin/env bash
# SPDX-License-Identifier: MPL-2.0
# Run with a Nerd Font: bash tools/admonition-demo.sh

printf '%b\n' \
    'All glyphs are Nerd Font characters. Option A matches Asciidoctor.' \
    'Current defaults: NOTE G, TIP E, IMPORTANT F, WARNING E, CAUTION B.' \
    '' \
    'DARK PALETTE' \
    'A  \033[48;2;121;192;255m\033[30m  NOTE \033[0m  nf-fa-info_circle              U+F05A' \
    'B  \033[48;2;121;192;255m\033[30m 󰋼 NOTE \033[0m  nf-md-information             U+F02FC' \
    'C  \033[48;2;121;192;255m\033[30m 󰋽 NOTE \033[0m  nf-md-information_outline     U+F02FD' \
    'D  \033[48;2;121;192;255m\033[30m  NOTE \033[0m  nf-cod-info                   U+EA74' \
    'E  \033[48;2;121;192;255m\033[30m  NOTE \033[0m  nf-seti-info                  U+E66A' \
    'F  \033[48;2;121;192;255m\033[30m  NOTE \033[0m  nf-oct-info                   U+F449' \
    'G  \033[48;2;121;192;255m\033[30m  NOTE \033[0m  nf-fa-info                    U+F129' \
    '' \
    'A  \033[48;2;63;185;80m\033[30m  TIP \033[0m  nf-fa-lightbulb_o              U+F0EB' \
    'B  \033[48;2;63;185;80m\033[30m 󰌵 TIP \033[0m  nf-md-lightbulb                U+F0335' \
    'C  \033[48;2;63;185;80m\033[30m 󰌶 TIP \033[0m  nf-md-lightbulb_outline        U+F0336' \
    'D  \033[48;2;63;185;80m\033[30m  TIP \033[0m  nf-cod-lightbulb               U+EA61' \
    'E  \033[48;2;63;185;80m\033[30m  TIP \033[0m  nf-oct-light_bulb              U+F400' \
    '' \
    'A  \033[48;2;188;140;255m\033[30m  IMPORTANT \033[0m  nf-fa-exclamation_circle       U+F06A' \
    'B  \033[48;2;188;140;255m\033[30m 󰀨 IMPORTANT \033[0m  nf-md-alert_circle             U+F0028' \
    'C  \033[48;2;188;140;255m\033[30m 󰗖 IMPORTANT \033[0m  nf-md-alert_circle_outline     U+F05D6' \
    'D  \033[48;2;188;140;255m\033[30m  IMPORTANT \033[0m  nf-cod-error                   U+EA87' \
    'E  \033[48;2;188;140;255m\033[30m  IMPORTANT \033[0m  nf-oct-alert                   U+F421' \
    'F  \033[48;2;188;140;255m\033[30m  IMPORTANT \033[0m  nf-oct-report                  U+F50A' \
    '' \
    'A  \033[48;2;255;123;114m\033[30m  WARNING \033[0m  nf-fa-warning                 U+F071' \
    'B  \033[48;2;255;123;114m\033[30m 󰀦 WARNING \033[0m  nf-md-alert                   U+F0026' \
    'C  \033[48;2;255;123;114m\033[30m 󰀪 WARNING \033[0m  nf-md-alert_outline           U+F002A' \
    'D  \033[48;2;255;123;114m\033[30m  WARNING \033[0m  nf-cod-warning                U+EA6C' \
    'E  \033[48;2;255;123;114m\033[30m  WARNING \033[0m  nf-oct-alert                  U+F421' \
    '' \
    'A  \033[48;2;210;153;34m\033[30m  CAUTION \033[0m  nf-fa-fire                    U+F06D' \
    'B  \033[48;2;210;153;34m\033[30m 󰈸 CAUTION \033[0m  nf-md-fire                    U+F0238' \
    'C  \033[48;2;210;153;34m\033[30m 󱗗 CAUTION \033[0m  nf-md-fire_alert              U+F15D7' \
    'D  \033[48;2;210;153;34m\033[30m  CAUTION \033[0m  nf-cod-flame                  U+EAF2' \
    'E  \033[48;2;210;153;34m\033[30m  CAUTION \033[0m  nf-oct-flame                  U+F490' \
    '' \
    'LIGHT PALETTE' \
    'A  \033[48;2;9;105;218m\033[97m  NOTE \033[0m  nf-fa-info_circle              U+F05A' \
    'B  \033[48;2;9;105;218m\033[97m 󰋼 NOTE \033[0m  nf-md-information             U+F02FC' \
    'C  \033[48;2;9;105;218m\033[97m 󰋽 NOTE \033[0m  nf-md-information_outline     U+F02FD' \
    'D  \033[48;2;9;105;218m\033[97m  NOTE \033[0m  nf-cod-info                   U+EA74' \
    'E  \033[48;2;9;105;218m\033[97m  NOTE \033[0m  nf-seti-info                  U+E66A' \
    'F  \033[48;2;9;105;218m\033[97m  NOTE \033[0m  nf-oct-info                   U+F449' \
    'G  \033[48;2;9;105;218m\033[97m  NOTE \033[0m  nf-fa-info                    U+F129' \
    '' \
    'A  \033[48;2;26;127;55m\033[97m  TIP \033[0m  nf-fa-lightbulb_o              U+F0EB' \
    'B  \033[48;2;26;127;55m\033[97m 󰌵 TIP \033[0m  nf-md-lightbulb                U+F0335' \
    'C  \033[48;2;26;127;55m\033[97m 󰌶 TIP \033[0m  nf-md-lightbulb_outline        U+F0336' \
    'D  \033[48;2;26;127;55m\033[97m  TIP \033[0m  nf-cod-lightbulb               U+EA61' \
    'E  \033[48;2;26;127;55m\033[97m  TIP \033[0m  nf-oct-light_bulb              U+F400' \
    '' \
    'A  \033[48;2;130;80;223m\033[97m  IMPORTANT \033[0m  nf-fa-exclamation_circle       U+F06A' \
    'B  \033[48;2;130;80;223m\033[97m 󰀨 IMPORTANT \033[0m  nf-md-alert_circle             U+F0028' \
    'C  \033[48;2;130;80;223m\033[97m 󰗖 IMPORTANT \033[0m  nf-md-alert_circle_outline     U+F05D6' \
    'D  \033[48;2;130;80;223m\033[97m  IMPORTANT \033[0m  nf-cod-error                   U+EA87' \
    'E  \033[48;2;130;80;223m\033[97m  IMPORTANT \033[0m  nf-oct-alert                   U+F421' \
    'F  \033[48;2;130;80;223m\033[97m  IMPORTANT \033[0m  nf-oct-report                  U+F50A' \
    '' \
    'A  \033[48;2;207;34;46m\033[97m  WARNING \033[0m  nf-fa-warning                 U+F071' \
    'B  \033[48;2;207;34;46m\033[97m 󰀦 WARNING \033[0m  nf-md-alert                   U+F0026' \
    'C  \033[48;2;207;34;46m\033[97m 󰀪 WARNING \033[0m  nf-md-alert_outline           U+F002A' \
    'D  \033[48;2;207;34;46m\033[97m  WARNING \033[0m  nf-cod-warning                U+EA6C' \
    'E  \033[48;2;207;34;46m\033[97m  WARNING \033[0m  nf-oct-alert                  U+F421' \
    '' \
    'A  \033[48;2;154;103;0m\033[97m  CAUTION \033[0m  nf-fa-fire                    U+F06D' \
    'B  \033[48;2;154;103;0m\033[97m 󰈸 CAUTION \033[0m  nf-md-fire                    U+F0238' \
    'C  \033[48;2;154;103;0m\033[97m 󱗗 CAUTION \033[0m  nf-md-fire_alert              U+F15D7' \
    'D  \033[48;2;154;103;0m\033[97m  CAUTION \033[0m  nf-cod-flame                  U+EAF2' \
    'E  \033[48;2;154;103;0m\033[97m  CAUTION \033[0m  nf-oct-flame                  U+F490' \
    '' \
    'Choose one letter per admonition, e.g. NOTE C, TIP A, IMPORTANT C, WARNING D, CAUTION B.'

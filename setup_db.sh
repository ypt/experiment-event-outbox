#!/bin/bash

dropdb -U experimenteventoutbox --if-exists experimenteventoutbox
createdb -U experimenteventoutbox -e -O experimenteventoutbox experimenteventoutbox

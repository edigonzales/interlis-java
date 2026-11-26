#!/bin/sh
set -eu

# Prepare the same local XTF and model inputs for both validator CLIs.
cd "$(dirname "$0")/.."
inputs="$PWD/build/validator-benchmark/inputs"
federal_models=https://models.geo.admin.ch
reference_models=https://models.interlis.ch
solothurn_data=https://data.geo.so.ch/files
solothurn_models=https://geo.so.ch/models
temporary_file=
trap 'if [ -n "$temporary_file" ]; then rm -f "$temporary_file"; fi' 0
trap 'exit 1' HUP INT TERM

publish() {
    if [ ! -s "$temporary_file" ]; then
        printf 'Empty benchmark input %s\n' "$1" >&2
        exit 1
    fi
    mv -f "$temporary_file" "$1"
    temporary_file=
}

download() {
    if [ -s "$1" ]; then return; fi
    temporary_file=$(mktemp "$1.XXXXXX")
    printf 'Downloading %s\n' "$2" >&2
    curl --fail --location --silent --show-error --retry 3 --output "$temporary_file" "$2"
    publish "$1"
}

extract() {
    if [ -s "$1" ]; then return; fi
    temporary_file=$(mktemp "$1.XXXXXX")
    unzip -p "$2" "$3" > "$temporary_file"
    publish "$1"
}

prepare_case() {
    case_dir="$inputs/$1"
    model_dir="$case_dir/models"
    mkdir -p "$model_dir"
}

prepare_archive() {
    download "$case_dir/data.zip" "$1"
    extract "$case_dir/data.xtf" "$case_dir/data.zip" "$2"
}

prepare_models_23() {
    download "$model_dir/CHBase_Part1_GEOMETRY_V1.ili" "$federal_models/CH/CHBase_Part1_GEOMETRY_V1.ili"
    download "$model_dir/Units.ili" "$reference_models/refhb23/Units-20120220.ili"
    download "$model_dir/CoordSys.ili" "$reference_models/$1"
}

prepare_case localities
prepare_archive \
    https://data.geo.admin.ch/ch.swisstopo-vd.ortschaftenverzeichnis_plz/ortschaftenverzeichnis_plz/ortschaftenverzeichnis_plz_2056.xtf.zip \
    AMTOVZ_INTERLIS24/OfficialIndexOfLocalities_V1_0.xtf
# The archive declares the original English model. Keep the supplied translation outside the model path.
extract "$model_dir/OfficialIndexOfLocalities_V1_0.ili" "$case_dir/data.zip" AMTOVZ_INTERLIS24/OfficialIndexOfLocalities_V1_0.ili
download "$case_dir/OfficialIndexOfLocalities_DE_V1_0.ili" "$federal_models/Swisstopo/OfficialIndexOfLocalities_DE_V1_0.ili"
download "$model_dir/CHBase_Part1_GEOMETRY_V2.ili" "$federal_models/CH/CHBase_Part1_GEOMETRY_V2.ili"
download "$model_dir/Units.ili" "$reference_models/refhb24/Units.ili"
download "$model_dir/CoordSys.ili" "$reference_models/refhb24/CoordSys.ili"
download "$model_dir/Text_V2.ili" "$reference_models/refhb24/Text_V2.ili"

prepare_case streets
prepare_archive \
    https://data.geo.admin.ch/ch.swisstopo.amtliches-strassenverzeichnis/amtliches-strassenverzeichnis_ch/amtliches-strassenverzeichnis_ch_2056.xtf.zip \
    amtliches-strassenverzeichnis_ch_2056.xtf
download "$model_dir/OfficialIndexOfStreets_V2_2.ili" "$federal_models/Swisstopo/OfficialIndexOfStreets_V2_2.ili"
prepare_models_23 refhb23/CoordSys.ili

prepare_case fff-ag
prepare_archive https://geodienste.ch/downloads/interlis/fruchtfolgeflaechen/AG/fruchtfolgeflaechen_AG_lv95.zip fff_AG.xtf
download "$model_dir/Fruchtfolgeflaechen_V1.ili" "$federal_models/ARE/Fruchtfolgeflaechen_V1.ili"
# The AG transfer header explicitly declares the 2015-11-24 CoordSys version.
prepare_models_23 obsolete/CoordSys-20151124_o1.ili

prepare_case so-buildings
download "$case_dir/data.xtf" "$solothurn_data/ch.so.agi.gebaeudeinformationen.xtf"
download "$model_dir/SO_AGI_Gebaeudeinformationen_Publikation_20250224.ili" \
    "$solothurn_models/AGI/SO_AGI_Gebaeudeinformationen_Publikation_20250224.ili"
download "$model_dir/CHBase_Part4_ADMINISTRATIVEUNITS_V1.ili" "$federal_models/CH/CHBase_Part4_ADMINISTRATIVEUNITS_V1.ili"
download "$model_dir/CHBase_Part2_LOCALISATION_V1.ili" "$federal_models/CH/CHBase_Part2_LOCALISATION_V1.ili"
prepare_models_23 refhb23/CoordSys.ili

prepare_case so-geology
download "$case_dir/data.xtf" "$solothurn_data/ch.so.afu.geologie.xtf"
download "$model_dir/SO_AFU_Geologie_20200831.ili" "$solothurn_models/AFU/SO_AFU_Geologie_20200831.ili"
prepare_models_23 refhb23/CoordSys.ili

prepare_case hazard-so
prepare_archive \
    https://geodienste.ch/downloads/interlis/gefahrenkarten/SO/gefahrenkarten_v1_3_SO_lv95.zip \
    naturgefahren.xtf
download "$model_dir/Hazard_Mapping_V1_3.ili" "$federal_models/BAFU/Hazard_Mapping_V1_3.ili"
prepare_models_23 refhb23/CoordSys.ili

prepare_case hazard-be
prepare_archive \
    https://geodienste.ch/downloads/interlis/gefahrenkarten/BE/gefahrenkarten_v1_3_BE_lv95.zip \
    hazard_mapping_lv95_v1_3_be.xtf
download "$model_dir/Hazard_Mapping_V1_3.ili" "$federal_models/BAFU/Hazard_Mapping_V1_3.ili"
prepare_models_23 refhb23/CoordSys.ili

for benchmark_case in localities streets fff-ag so-buildings so-geology hazard-so hazard-be; do
    case_dir="$inputs/$benchmark_case"
    printf '%s\t%s\t%s\n' "$benchmark_case" "$case_dir/data.xtf" "$case_dir/models"
done

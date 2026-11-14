[out:json][timeout:60];
nwr["addr:housenumber"]{{area}}->.addresses;
way["building"]{{area}}->.buildings;
node["entrance"]{{area}}->.entrances;
.addresses out center;
.buildings out geom;
.entrances out;

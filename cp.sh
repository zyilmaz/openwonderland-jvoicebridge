WONDERLAND_DIR=/Users/bh37721/src/wonderland
echo copying dist/softphone/softphone.jar to $WONDERLAND_DIR/core/ext/softphone/softphone.jar
cp dist/softphone/softphone.jar $WONDERLAND_DIR/core/ext/softphone/softphone.jar

echo copying stun/build/lib/stun.jar to $WONDERLAND_DIR/core/ext/common
cp stun/build/lib/stun.jar $WONDERLAND_DIR/core/ext/common

echo copying dist/bridge/voip.jar to $WONDERLAND_DIR/modules/tools/audio-manager/lib
cp dist/bridge/voip.jar $WONDERLAND_DIR/modules/tools/audio-manager/lib

echo copying dist/bridge/bridge_connector.jar to  $WONDERLAND_DIR/modules/tools/audio-manager/lib
cp dist/bridge/bridge_connector.jar $WONDERLAND_DIR/modules/tools/audio-manager/lib

echo copying dist/voicelib/voicelib.jar to $WONDERLAND_DIR/modules/tools/audio-manager/lib
cp dist/voicelib/voicelib.jar $WONDERLAND_DIR/modules/tools/audio-manager/lib

echo copying dist/voicebridge-dist.zip to $WONDERLAND_DIR/modules/tools/voicebridge/lib/zip
cp dist/voicebridge-dist.zip $WONDERLAND_DIR/modules/tools/voicebridge/lib/zip

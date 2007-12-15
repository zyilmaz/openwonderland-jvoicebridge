echo "Make Header..."
javah -classpath ../../build/lib/softphone.jar com.sun.mc.softphone.media.coreaudio.AudioDriverMac
echo "Complie C++..."
g++ -c -g -I/System/Library/Frameworks/JavaVM.framework/Headers/ -I/System/Library/Frameworks/CoreAudio.framework/Headers/ AudioDriverMac.cpp
echo "Link C++..."
g++ -g -dynamiclib -o libMediaFramework.jnilib AudioDriverMac.o -framework JavaVM -framework CoreAudio -framework AudioUnit -framework AudioToolBox -framework Carbon

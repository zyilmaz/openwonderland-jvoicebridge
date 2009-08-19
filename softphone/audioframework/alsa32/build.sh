echo "Make Header..."
javah -classpath ../../build/lib/softphone.jar com.sun.mc.softphone.media.alsa.AudioDriverAlsa
echo "Compile..."
gcc -c -O AudioDriverAlsa.c Setup.c GetAudioDevices.c
echo "Link..."
gcc -shared -W1,-soname,libMediaFramework32.so -o libMediaFramework32.so AudioDriverAlsa.o Setup.o GetAudioDevices.o -lasound

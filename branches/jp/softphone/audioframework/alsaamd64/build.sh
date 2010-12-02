JAVA_INCLUDE_DIRS="-I/usr/lib/jvm/java-6-sun/include -I/usr/lib/jvm/java-6-sun/include/linux"

rm *.o *.so

echo "Make Header..."
javah -classpath ../../build/lib/softphone.jar com.sun.mc.softphone.media.alsa.AudioDriverAlsa
echo "Compile..."
gcc -c -fPIC -O AudioDriverAlsa.c Setup.c GetAudioDevices.c $JAVA_INCLUDE_DIRS
echo "Link..."
gcc -shared -W1,-soname,libMediaFrameworkAmd64.so -o libMediaFrameworkAmd64.so AudioDriverAlsa.o Setup.o GetAudioDevices.o -lasound

cd ..
javac DosSend.java

java DosSend
cp DosOok_message.wav lp_filter/DosOok_message.wav

cd lp_filter
javac lpFilter1Etude.java
javac lpFilter2Etude.java

java lpFilter1Etude ./DosOok_message.wav
java lpFilter2Etude ./DosOok_message.wav
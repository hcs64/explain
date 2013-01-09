AwtTest.jar: AwtTest.java
	javac -cp bsh-2.2.0.jar:etherpad_lite_client.jar AwtTest.java ExposeMethodSpec.java GraphicsWrapper.java 
	jar cf awttest.jar *.class

clean:
	rm *.class awttest.jar

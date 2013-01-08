AwtTest.jar: AwtTest.java
	javac -cp bsh-2.1b5.jar AwtTest.java ExposeMethodSpec.java GraphicsWrapper.java ColorWrapper.java
	jar cf awttest.jar *.class

clean:
	rm *.class awttest.jar

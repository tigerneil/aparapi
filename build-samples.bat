setlocal 
call setvars.bat

rm -r -f ^
  samples.jar ^
  classes 

mkdir classes

%LAMBDA_JAVA_HOME%\bin\javac ^
 -classpath aparapi.jar ^
 -XDlambdaToMethod ^
 -g ^
 -d classes ^
 -sourcepath src\java^
 src\java\com\amd\aparapi\samples\Mandel.java 
rem src\java\com\amd\aparapi\samples\Squares.java 

%LAMBDA_JAVA_HOME%\bin\jar ^
  cf samples.jar^
  -C classes^
  com

endlocal 

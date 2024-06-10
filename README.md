## Using Maven with IntelliJ Idea
You can download IntelliJ Idea from Jetbrains. The community edition is free to use. We also recommend installing the ANTLR v4 grammar plugin, which brings syntax highlighting to ANTLR grammars that are used in the first assignment.

After the installation you can just open the folder containing the pom.xml. This will load the Maven project. To compile, open the Maven pane by clicking on the button labelled Maven button on the right side of the window. Then expand the tree so you can double click on crux > Lifecycle > compile.

Other interesting commands are test to run all unit tests and package to create .jar-file in the folder target to run the compiler easily from the command line:

``` crux/target $ java -jar crux-1.0-jar-with-dependencies.jar my-program.crux ```

To run and debug the compiler from within IntelliJ Idea, open the Compiler class and click on the green arrow next to the class definition by clicking on Run 'Compiler.main()'. To provide additional arguments click on Edit 'Compiler.main()'... and add them in the designated field.

Alternatively, you can also run Maven from the command line. for Maven. Make also sure, you have at least Java 11 installed and available in your path.

clean -  deletes the build directory (target)

validate - verify all necessary information is available

compile - compile the source code of the project

test - test the compiled source code using a unit testing framework

package - create a JAR file from the compiled source code

verify - run any checks on results of integration tests

install - install the package into the local repository.

deploy - deploy the final package to the remote repository


## Test different Stages
Please change the value of variable TEST_TO_RUN at

``` src/test/java/CompilerStageTests.java, line 28 ```

into

``` private final String[] TEST_TO_RUN = {"stage#"}; ```

to use test cases for project # when you run mvn test. Or to use the following one to run both project 1 and project 2 test cases:

``` private final String[] TEST_TO_RUN = {"stage1", "stage2"}; ```

From UCI CS142A Professor Brian Demsky

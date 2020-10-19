# Compile.
mvn compile

# Generate all dependencies in target/lib (${project.build.directory}/lib in pom.xml).
mvn dependency:copy-dependencies

# Start Studio reverse proxy from the root of the Maven project passing arguments.
java -cp "target/lib/*:target/classes" com.jpmc.sagemaker.studio.StudioReverseProxyService \
    --domain=desktop --realm=us-east-1 --root=build/private

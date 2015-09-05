import spock.lang.Specification

class ScriptSpecification extends Specification {

  static String containerName
  static String scriptsDir
  static String fakesDir
  static String workspacesDir

  def setupSpec() {
    containerName = this.getClass().getName()

    getSharedDirectoriesPaths()

    createWorkspacesFolder()

    runTestContainer()
  }

  def getSharedDirectoriesPaths() {
    scriptsDir = System.getProperty('scriptsDir')
    fakesDir = System.getProperty('fakesDir')
    workspacesDir = System.getProperty('workspacesDir')
  }

  def runTestContainer() {
    final String dockerRun = 'docker run'
    final String volumeFlags = "--volume ${scriptsDir}:/scripts " +
        "--volume ${fakesDir}:/mocks " +
        "--volume ${workspacesDir}/${containerName}:/workspace"
    final String dockerFlags = "--detach " +
        "${volumeFlags} " +
        "--workdir=/workspace " +
        "-e PATH=/mocks:/bin:/usr/bin " +
        "--name ${containerName}"
    final String imageName = 'centos:latest'
    final String command = 'sleep 1d'

    "${dockerRun} ${dockerFlags} ${imageName} ${command}".execute().waitFor()
  }

  def createWorkspacesFolder() {
    //Docker creates directories mounted by --volume automatically, but it
    //creates them with the root as the owner. We create necessary directories
    //ourselves to have proper dir ownership and permissions.
    new File(workspacesDir).mkdir()
  }

  Process runCommand(final String command) {
    final String dockerExec = 'docker exec'
    ('docker exec -t ' + containerName + ' ' + command).execute()
  }

  def 'should run in a container named after specification class'() {
    expect:
    containerName == 'ScriptSpecification'
  }

  def 'should successfully run the container for test purposes'() {
    expect:
    'docker ps'.execute().text.contains(containerName)
  }

  def 'should be able to run arbitrary shell command'() {
    expect:
    runCommand('echo -n hello').text == 'hello'
  }

  def 'should be able to run script under test'() {
    expect:
    runCommand('/scripts/helloworld.sh').text == 'Hello world!\r\n'
  }

  def 'should run inside the dedicated workspace'() {
    expect:
    runCommand('pwd').text.trim() == "/workspace"
  }

  def 'should run fakes instead of actual binaries'() {
    expect:
    runCommand('df').text.trim() == "<disk usage>"
  }

  def cleanupSpec() {
    restoreAccessPermissionsForWorkspaces()
    killAndRemoveTestContainer()
  }

  def restoreAccessPermissionsForWorkspaces() {
    final String uid = 'id --user'.execute().text.trim()
    final String gid = 'id --group'.execute().text.trim()
    runCommand("chown --recursive ${uid}:${gid} /workspaces").waitFor()
  }

  def killAndRemoveTestContainer() {
    "docker rm --force --volumes ${containerName}".execute().waitFor()
  }
}

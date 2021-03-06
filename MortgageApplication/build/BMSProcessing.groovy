import com.ibm.team.dbb.repository.*
import com.ibm.team.dbb.dependency.*
import com.ibm.team.dbb.build.*

// receive passed arguments
def file = args[0]
println("* Building $file using ${this.class.getName()}.groovy script")

// define local properties
def properties = BuildProperties.getInstance()
def bmsPDS = "${properties.hlq}.BMS"
def copybookPDS = "${properties.hlq}.COPYBOOK"
def loadPDS = "${properties.hlq}.LOAD"
def objectPDS = "${properties.hlq}.OBJ"
def member = CopyToPDS.createMemberName(file)
def maxRC = 8

// define the BPXWDYN options for allocated temporary datasets
def tempCreateOptions = "tracks space(5,5) unit(vio) blksize(80) lrecl(80) recfm(f,b) new"

//*
// copy program to PDS 
//*
println("Copying ${properties.sourceDir}/$file to $bmsPDS($member)")
def copyProgram = new CopyToPDS().file(new File("${properties.sourceDir}/$file"))
                                 .dataset(bmsPDS)
                                 .member(member)
copyProgram.copy()


//*
// Process BMS Map
//*
println("Processing BMS Map $file")	

// define the MVSExec command for generating the BMS copybook
def copybookGen = new MVSExec().file(file)
 			   .pgm("ASMA90")
                           .parm("SYSPARM(DSECT),DECK,NOOBJECT")
                           .attachx(true)
           
// add DD statements to the copybookGen command
copybookGen.dd(new DDStatement().name("SYSIN").dsn("$bmsPDS($member)").options("shr").report(true))
copybookGen.dd(new DDStatement().name("SYSPUNCH").dsn("$copybookPDS($member)").options("shr").output(true))
copybookGen.dd(new DDStatement().name("SYSPRINT").options(tempCreateOptions))
copybookGen.dd(new DDStatement().name("SYSUT1").options(tempCreateOptions))
copybookGen.dd(new DDStatement().name("SYSUT2").options(tempCreateOptions))
copybookGen.dd(new DDStatement().name("SYSUT3").options(tempCreateOptions))
copybookGen.dd(new DDStatement().name("SYSLIB").dsn(properties.SDFHMAC).options("shr"))
copybookGen.dd(new DDStatement().dsn(properties.MACLIB).options("shr"))
copybookGen.dd(new DDStatement().name("TASKLIB").dsn(properties.SASMMOD1).options("shr"))

// add a copy command to the copybookGen command to copy the SYSPRINT from the temporary dataset to an HFS log file
copybookGen.copy(new CopyToHFS().ddName("SYSPRINT").file(new File("${properties.buildDir}/${member}.log")).encoding(properties.logEncoding))

// define the MVSExec command to compile the BMS map
def compile = new MVSExec().file(file)
 			   .pgm("ASMA90")
                           .parm("SYSPARM(MAP),DECK,NOOBJECT")
                           .attachx(true)

// add DD statements to the compile command
compile.dd(new DDStatement().name("SYSIN").dsn("$bmsPDS($member)").options("shr"))
compile.dd(new DDStatement().name("SYSPUNCH").dsn("&&TEMPOBJ").options(tempCreateOptions).pass(true))
compile.dd(new DDStatement().name("SYSPRINT").options(tempCreateOptions))
compile.dd(new DDStatement().name("SYSUT1").options(tempCreateOptions))
compile.dd(new DDStatement().name("SYSUT2").options(tempCreateOptions))
compile.dd(new DDStatement().name("SYSUT3").options(tempCreateOptions))
compile.dd(new DDStatement().name("SYSLIB").dsn(properties.SDFHMAC).options("shr"))
compile.dd(new DDStatement().dsn(properties.MACLIB).options("shr"))
compile.dd(new DDStatement().name("TASKLIB").dsn(properties.SASMMOD1).options("shr"))

// add a copy command to the compile command to copy the SYSPRINT from the temporary dataset to an HFS log file
compile.copy(new CopyToHFS().ddName("SYSPRINT").file(new File("${properties.buildDir}/${member}.log")).encoding(properties.logEncoding).append(true))


// define the MVSExec command to link edit the program
def linkedit = new MVSExec().file(file)
	 	            .pgm("IEWBLINK")
	                    .parm("MAP,RENT,COMPAT(PM5)")
	                    
// add DD statements to the linkedit command
linkedit.dd(new DDStatement().name("SYSLIN").dsn("&&TEMPOBJ").options("shr"))
linkedit.dd(new DDStatement().name("SYSLMOD").dsn("$loadPDS($member)").options("shr").output(true))
linkedit.dd(new DDStatement().name("SYSPRINT").options(tempCreateOptions))
linkedit.dd(new DDStatement().name("SYSUT1").options(tempCreateOptions))
linkedit.dd(new DDStatement().name("SYSLIB").dsn(objectPDS).options("shr"))
linkedit.dd(new DDStatement().dsn(properties.SCEELKED).options("shr"))
linkedit.dd(new DDStatement().dsn(properties.SDFHLOAD).options("shr"))

// add a copy command to the linkedit command to append the SYSPRINT from the temporary dataset to the HFS log file
linkedit.copy(new CopyToHFS().ddName("SYSPRINT").file(new File("${properties.buildDir}/${member}.log")).encoding(properties.logEncoding).append(true))


// define and execute a simple MVSJob to handle passed temporary DDs between MVSExec commands
def job = new MVSJob().executable(copybookGen)
                      .executable(compile)
                      .executable(linkedit)
                      .maxRC(maxRC)
job.execute()

  

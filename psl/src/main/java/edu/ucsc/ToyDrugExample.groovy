//    implement toy cell line sensitity to drug exaxmple

package edu.ucsc.cancer

import java.text.DecimalFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;

import edu.umd.cs.psl.application.inference.MPEInference;
import edu.umd.cs.psl.application.learning.weight.maxlikelihood.MaxLikelihoodMPE;
import edu.umd.cs.psl.config.*
import edu.umd.cs.psl.database.DataStore
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.DatabasePopulator;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.ReadOnlyDatabase;
import edu.umd.cs.psl.database.rdbms.RDBMSDataStore
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver.Type
import edu.umd.cs.psl.groovy.PSLModel;
import edu.umd.cs.psl.groovy.PredicateConstraint;
import edu.umd.cs.psl.groovy.SetComparison;
import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.UniqueID;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.function.ExternalFunction;
import edu.umd.cs.psl.ui.functions.textsimilarity.*
import edu.umd.cs.psl.ui.loading.InserterUtils;
import edu.umd.cs.psl.util.database.Queries;

////////////////////////// initial setup ////////////////////////
ConfigManager cm = ConfigManager.getManager()
ConfigBundle config = cm.getBundle("toy-drug-example")

def defaultPath = System.getProperty("java.io.tmpdir")
String dbpath = config.getString("dbpath", defaultPath + File.separator + "toy-drug-example")
DataStore data = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk, dbpath, true), config)
PSLModel m = new PSLModel(this, data)

////////////////////////// predicate declaration ////////////////////////
m.add predicate: "Drug",       types: [ArgumentType.UniqueID, ArgumentType.String]
m.add predicate: "Gene",       types: [ArgumentType.UniqueID, ArgumentType.String]
m.add predicate: "Cell",     types: [ArgumentType.UniqueID, ArgumentType.String]
m.add predicate: "Targets",        types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "Essential",        types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "Active",        types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "Sensitive",        types: [ArgumentType.UniqueID, ArgumentType.UniqueID]

///////////////////////////// rules ////////////////////////////////////
m.add rule : ( Targets(D1, G1) & Essential(C1, G1) ) >> Sensitive(C1, D1),  weight : 10
m.add rule : ( Targets(D1, G1) & Active(C1, G1) ) >> Sensitive(C1, D1),  weight : 5
m.add rule: ~Sensitive(C1, D1), weight: 2

println m;

//////////////////////////// data setup ///////////////////////////
// loads data
def dir = 'data'+java.io.File.separator;
def evidencePartition = new Partition(0);

insert = data.getInserter(Drug, evidencePartition);
InserterUtils.loadDelimitedData(insert, dir+"drug.txt");

insert = data.getInserter(Gene, evidencePartition);
InserterUtils.loadDelimitedData(insert, dir+"gene.txt");

insert = data.getInserter(Cell, evidencePartition);
InserterUtils.loadDelimitedData(insert, dir+"cell.txt");

insert = data.getInserter(Targets, evidencePartition);
InserterUtils.loadDelimitedData(insert, dir+"targets.txt");

insert = data.getInserter(Essential, evidencePartition);
InserterUtils.loadDelimitedDataTruth(insert, dir+"essential.txt");

insert = data.getInserter(Active, evidencePartition);
InserterUtils.loadDelimitedDataTruth(insert, dir+"active.txt");


// add target atoms
def targetPartition = new Partition(1);
insert = data.getInserter(Sensitive, targetPartition);
InserterUtils.loadDelimitedData(insert, dir+"sensitive_target.txt");


Database db = data.getDatabase(targetPartition, [Drug, Gene, Cell, Targets, Essential, Active] as Set, evidencePartition);

//////////////////////////// run inference ///////////////////////////
MPEInference inferenceApp = new MPEInference(m, db, config);
inferenceApp.mpeInference();
inferenceApp.close();

println "Inference results with hand-defined weights:"
DecimalFormat formatter = new DecimalFormat("#.##");
for (GroundAtom atom : Queries.getAllAtoms(db, Sensitive))
    println atom.toString() + "\t" + formatter.format(atom.getValue());


/*

//////////////////////////// weight learning ///////////////////////////
Partition trueDataPartition = new Partition(2);
insert = data.getInserter(SamePerson, trueDataPartition)
InserterUtils.loadDelimitedDataTruth(insert, dir + "sn_align.txt");

Database trueDataDB = data.getDatabase(trueDataPartition, [samePerson] as Set);
MaxLikelihoodMPE weightLearning = new MaxLikelihoodMPE(m, db, trueDataDB, config);
weightLearning.learn();
weightLearning.close();

println ""
println "Learned model:"
println m

// close the Databases to flush writes
db.close();
trueDataDB.close();
*/

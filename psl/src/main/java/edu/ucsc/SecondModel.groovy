//    implement cell line sensitity to drug exaxmple

package edu.ucsc

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
import edu.umd.cs.psl.evaluation.statistics.*;


nfold = this.args[0] as int
for (i=1; i<= nfold; i++) {
    println "fold ${i}"
    ////////////////////////// initial setup ////////////////////////
    ConfigManager cm = ConfigManager.getManager()
    ConfigBundle config = cm.getBundle("first-model")
    
    def defaultPath = System.getProperty("java.io.tmpdir")
    String dbpath = config.getString("dbpath", defaultPath + File.separator + "first-model")
    DataStore data = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk, dbpath, true), config)
    PSLModel m = new PSLModel(this, data)
    
    ////////////////////////// predicate declaration ////////////////////////
    m.add predicate: "Drug",       types: [ArgumentType.UniqueID, ArgumentType.String]
    m.add predicate: "Gene",       types: [ArgumentType.UniqueID, ArgumentType.String]
    m.add predicate: "Cell",     types: [ArgumentType.UniqueID, ArgumentType.String]
    m.add predicate: "DrugTarget",        types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
    m.add predicate: "Essential",        types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
    m.add predicate: "Active",        types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
    m.add predicate: "Sensitive",        types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
   
    ///////////////////////////// rules ////////////////////////////////////
    m.add rule : ( DrugTarget(D, G) & Essential(C, G))  >> Sensitive(C, D),  weight : 10
    m.add rule : ( DrugTarget(D, G) & Active(C, G) ) >> Sensitive(C, D),  weight : 5
    m.add rule: ~Sensitive(C, D), weight: 2
    
    println ""
    println "Rules with initial weights:"
    println m;
    
    //////////////////////////// data setup ///////////////////////////
    // loads data
    def dir = 'data'+java.io.File.separator +"first_model"+java.io.File.separator;
    def target_dir = "seed0" + java.io.File.separator + "cross_val" + java.io.File.separator + "5fold" + java.io.File.separator;
    def evidencePartition = new Partition(0);
    
    insert = data.getInserter(Drug, evidencePartition);
    InserterUtils.loadDelimitedData(insert, dir+"drug.txt");
    
    insert = data.getInserter(Gene, evidencePartition);
    InserterUtils.loadDelimitedData(insert, dir+"gene.txt");
    
    insert = data.getInserter(Cell, evidencePartition);
    InserterUtils.loadDelimitedData(insert, dir+"cell.txt");
    
    insert = data.getInserter(DrugTarget, evidencePartition);
    InserterUtils.loadDelimitedData(insert, dir+"drug_target.txt");
    
    insert = data.getInserter(Essential, evidencePartition);
    InserterUtils.loadDelimitedDataTruth(insert, dir+"not_essential.txt");
    
    insert = data.getInserter(Active, evidencePartition);
    InserterUtils.loadDelimitedDataTruth(insert, dir+"active.txt");
    
    
    // add target atoms
    def targetPartition = new Partition(1);
    insert = data.getInserter(Sensitive, targetPartition);
    InserterUtils.loadDelimitedData(insert, dir + "sensitive_target.txt");
    
    Database db = data.getDatabase(targetPartition, [Drug, Gene, Cell, DrugTarget, Essential, Active] as Set, evidencePartition);
    
    //////////////////////////// weight learning ///////////////////////////
    Partition trueDataPartition = new Partition(2);
    insert = data.getInserter(Sensitive, trueDataPartition)
    InserterUtils.loadDelimitedDataTruth(insert, dir+target_dir+"fold${i}_train.txt");
    
    Database trueDataDB = data.getDatabase(trueDataPartition, [Sensitive] as Set);
    MaxLikelihoodMPE weightLearning = new MaxLikelihoodMPE(m, db, trueDataDB, config);
    weightLearning.learn();
    weightLearning.close();
    
    println ""
    println "Learned model:"
    println m

    
    //////////////////////////// run inference ///////////////////////////
    MPEInference inferenceApp = new MPEInference(m, db, config);
    inferenceApp.mpeInference();
    inferenceApp.close();
    
    println "saving inference results to result/"
    DecimalFormat formatter = new DecimalFormat("#.#######");
    def result_file = new File("result/second_model/cross_val_fold${i}_result.txt");
    result_file.write ""
    for (GroundAtom atom : Queries.getAllAtoms(db, Sensitive)) {
        for (int i=0; i<2; i++) {
            result_file << atom.arguments[i].toString() + "\t"
        }
        result_file << formatter.format(atom.getValue()) + "\n"}
    
    // close the Databases to flush writes
    db.close();
    trueDataDB.close();
}

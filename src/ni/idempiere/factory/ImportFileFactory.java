package ni.idempiere.factory;

import org.adempiere.base.IProcessFactory;
import org.compiere.process.ProcessCall;

import ni.idempiere.process.ImportFile;

public class ImportFileFactory implements IProcessFactory{

	@Override
	public ProcessCall newProcessInstance(String className) {
		if(className.equalsIgnoreCase("ni.idempiere.process.ImportFile")) 
			return new ImportFile();
		
		return null;
	}

}

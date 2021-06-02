package ni.idempiere.process;

import org.compiere.model.NaturalAccountMap;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.CLogger;
import org.compiere.util.Ini;
import org.compiere.util.Util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.StringTokenizer;
import java.util.logging.Level;

public class ImportFile extends SvrProcess{
	
	private String path;

	@Override
	protected void prepare() {
		ProcessInfoParameter[] para = getParameter();
		for (int i = 0; i < para.length; i++)
		{
			String name = para[i].getParameterName();
			if (para[i].getParameter() == null)
				;
			if (name.equals("param1"))
				path = (String) para[i].getParameter();
			else
				log.log(Level.SEVERE, "Unknown Parameter: " + name);
		}
		
	}

	@Override
	protected String doIt() throws Exception {
		
		File file = new File(path);
		
		parseFile(file);

		return null;
	}
	
	/**	Logger			*/
	private static CLogger log = CLogger.getCLogger(NaturalAccountMap.class);

	/**
	 *  Read and Parse File
	 * 	@param file Accounts file
	 *  @return error message or "" if OK
	 */
	public String parseFile (File file)
	{
		if (log.isLoggable(Level.CONFIG)) log.config(file.getAbsolutePath());
		String line = null;
		try
		{
			//  see FileImport
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), Ini.getCharset()), 10240);
			//	not safe see p108 Network pgm
			String errMsg = "";

			//  read lines
			int lineNo= 1;
			while ((line = in.readLine()) != null && errMsg.length() == 0) {
				errMsg = parseLine(line, lineNo);
				lineNo++;
			}
			line = null;
			in.close();

			//  Error
			if (errMsg.length() != 0)
				return errMsg;
		}
		catch (Exception ioe)
		{
			String s = ioe.getLocalizedMessage();
			if (s == null || s.length() == 0)
				s = ioe.toString();
			return "Parse Error: Line=" + line + " - " + s;
		}
		return "";
	}   //  parse

	/**
	 *  Create Account Entry for Default Accounts only.
	 *  @param line line with info
	 *  Line format (9 fields)
	 *   1	A   [Account Value]
	 *   2	B   [Account Name]
	 *   3	C   [Description]
	 *   4	D   [Account Type]
	 *   5	E   [Account Sign]
	 *   6	F   [Document Controlled]
	 *   7	G   [Summary Account]
	 * 	 8	H   [Default_Account]
	 * 	 9	I   [Parent Value] - ignored
	 *
	 *  @return error message or "" if OK
	 *  @throws Exception
	 */
	public String parseLine (String line, int lineNo) throws Exception
	{
		if (log.isLoggable(Level.CONFIG)) log.config(lineNo+" : "+line);

		if (line.trim().length()==0) {
			log.log(Level.WARNING, "Line "+lineNo+" is empty, ignored. ");
			return "";
		}
		
		//  Fields with ',' are enclosed in "
		StringBuilder newLine = new StringBuilder();
		StringTokenizer st = new StringTokenizer(line, "\"", false);
		if ((st==null )||(st.countTokens()==0)) {
			log.log(Level.SEVERE, "Parse error: No \\\" found in line: "+lineNo);
			return "";
		}
		newLine.append(st.nextToken());         //  first part
		while (st.hasMoreElements())
		{
			String s = st.nextToken();          //  enclosed part
			newLine.append(s.replace(',',' ')); //  remove ',' with space
			if (st.hasMoreTokens())
				newLine.append(st.nextToken()); //  unenclosed
		}
		//  add space at the end        - tokenizer does not count empty fields
		newLine.append(" ");

		//  Parse Line - replace ",," with ", ,"    - tokenizer does not count empty fields
		String pLine = Util.replace(newLine.toString(), ",,", ", ,");
		pLine = Util.replace(pLine, ",,", ", ,");
		st = new StringTokenizer(pLine, ",", false);
		//  All fields there ?
		if (st.countTokens() == 1)
		{
			log.log(Level.SEVERE, "Ignored: Require ',' as separator - " + pLine);
			return "";
		}
		if (st.countTokens() < 16)
		{
			log.log(Level.SEVERE, "Ignored: FieldNumber wrong: " + st.countTokens() + " - " + pLine);
			return "";
		}

		//  Fill variables
		String Value = null, Name = null, Description = null,
			AccountType = null, AccountSign = null, IsDocControlled = null,
			IsSummary = null, Default_Account = null;
		//
		for (int i = 0; i < 15 && st.hasMoreTokens(); i++)
		{
			String s = st.nextToken().trim();
			//  Ignore, if is it header line
			if (s == null)
				s = "";
			if (s.startsWith("[") && s.endsWith("]"))
				return "";
			//
			if (i == 0)			//	A - Value
				Value = s;
			else if (i == 1)	//	B - Name
				Name = s;
			else if (i == 2)	//	C - Description
				Description = s;
			else if (i == 3)	//	D - Type
				AccountType = s.length()>0 ? String.valueOf(s.charAt(0)) : "E";
			else if (i == 4)	//	E - Sign
				AccountSign = s.length()>0 ? String.valueOf(s.charAt(0)) : "N";
			else if (i == 5)	//	F - DocControlled
				IsDocControlled = s.length()>0 ? String.valueOf(s.charAt(0)) : "N";
			else if (i == 6)	//	G - IsSummary
				IsSummary = s.length()>0 ? String.valueOf(s.charAt(0)) : "N";
			else if (i == 7)	//	H - Default_Account
				Default_Account = s;
		}

		//	Ignore if Value & Name are empty (no error message)
		if ((Value == null || Value.length() == 0) && (Name == null || Name.length() == 0))
			return "";

		//  Default Account may be blank
		if (Default_Account == null || Default_Account.length() == 0)
		//	Default_Account = String.valueOf(s_keyNo++);
			return "";

		//	No Summary Account
		if (IsSummary == null || IsSummary.length() == 0)
			IsSummary = "N";
		if (!"SUMMARY".equals(Default_Account) && !IsSummary.equals("N"))
			return "";
			
		//  Validation
		if (AccountType == null || AccountType.length() == 0)
			AccountType = "E";
			
		if (AccountSign == null || AccountSign.length() == 0)
			AccountSign = "N";
		if (IsDocControlled == null || IsDocControlled.length() == 0)
			IsDocControlled = "N";


	//	log.config( "Value=" + Value + ", AcctType=" + AccountType
	//		+ ", Sign=" + AccountSign + ", Doc=" + docControlled
	//		+ ", Summary=" + summary + " - " + Name + " - " + Description);

		/*try
		{
			//	Try to find - allows to use same natural account for multiple default accounts 
			MElementValue na = (MElementValue)m_valueMap.get(Value);
			if (na == null)
			{
				//  Create Account - save later
				na = new MElementValue(m_ctx, Value, Name, Description,
					AccountType, AccountSign,
					IsDocControlled.toUpperCase().startsWith("Y"), 
					IsSummary.toUpperCase().startsWith("Y"), m_trxName);
				m_valueMap.put(Value, na);
			}
			
			//  Add to Cache
			put((K)Default_Account.toUpperCase(), (V)na);
		}
		catch (Exception e)
		{
			return (e.getMessage());
		}*/

		return "";
	}

}

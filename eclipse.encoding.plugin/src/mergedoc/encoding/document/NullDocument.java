package mergedoc.encoding.document;

import org.eclipse.ui.IEditorPart;

import mergedoc.encoding.IActiveDocumentAgentCallback;

/**
 * This is a dummy handler for ActiveDocumentAgent.
 * @author Tsoi Yat Shing
 * @author Shinji Kashihara
 */
public class NullDocument extends ActiveDocument {

	public NullDocument() {
		super(null, null);
	}
	
	@Override
	protected void init(IEditorPart editor, IActiveDocumentAgentCallback callback) {
	}
	
	@Override
	public String getFileName() {
		return null;
	}
	
	@Override
	public void propertyChanged(Object source, int propId) {
	}
	
	@Override
	public void setEncoding(String encoding) {
	}
}

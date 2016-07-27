package mergedoc.encoding;

import static java.lang.String.*;
import static mergedoc.encoding.Activator.*;
import static mergedoc.encoding.Langs.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.ide.IDEEncoding;
import org.eclipse.ui.internal.dialogs.ContentTypesPreferencePage;
import org.osgi.service.prefs.Preferences;

import mergedoc.encoding.EncodingPreferenceInitializer.PreferenceKey;
import mergedoc.encoding.document.ActiveDocument;

/**
 * Encoding label shown in the status bar.
 * @author Shinji Kashihara
 */
@SuppressWarnings("restriction")
public class EncodingLabel implements PreferenceKey {

	private final ActiveDocumentAgent agent;
	private final CLabel label;
	private Menu popupMenu;
	private String creationEncoding;

	private static class EncodingItem {
		public String encoding;
		public String menuText;
	}

	public EncodingLabel(ActiveDocumentAgent agent, Composite statusBar, int widthHint) {
		this.agent = agent;
		label = new CLabel(statusBar, SWT.LEFT);
		GridData gridData = new GridData();
		gridData.widthHint = widthHint;
		label.setLayoutData(gridData);
	}

	public void initMenu() {

		ActiveDocument doc = agent.getDocument();
		if (doc.getCurrentEncoding() == null) {
			label.setText(null);
			label.setToolTipText(null);
			label.setImage(null);
			label.setMenu(null);
			return;
		}
		label.setText(doc.getCurrentEncodingLabel());

		if (doc.mismatchesEncoding() && prefIs(PREF_AUTODETECT_WARN)) {
			String message = "Detected charset %s (Current setting: %s)";
			doc.warnMessage(message, doc.getDetectedCharset(), doc.getCurrentEncoding());
			label.setToolTipText(format(message, doc.getDetectedCharset(), doc.getCurrentEncoding()));
			label.setImage(Activator.getImage("warn"));
		} else {
			label.setImage(null);
			if (doc.canChangeFileEncoding()) {
				label.setToolTipText(format("Right-click to change the encoding of '%s'", doc.getFileName()));
			} else {
				label.setToolTipText(null);
			}
		}

		if (popupMenu != null && !popupMenu.isDisposed()) {
			label.setMenu(popupMenu);
			return;
		}
		popupMenu = new Menu(label);
		label.setMenu(popupMenu);

		// Add the menu items dynamically
		popupMenu.addMenuListener(new MenuAdapter() {
			@Override
			public void menuShown(MenuEvent e) {

				final ActiveDocument doc = agent.getDocument();
				doc.warnDirtyMessage(agent.isDocumentDirty() && doc.canChangeFileEncoding());

				// Remove existing menu items.
				for (MenuItem item: popupMenu.getItems()) item.dispose();

				createSettingMenu();
				createShortcutMenu();
				createSelectionMenu();
			}
		});
	}

	private void createSettingMenu() {

		createSettingMenuItem(PREF_AUTODETECT_CHANGE, "Autodetect: Set Automatically");
		createSettingMenuItem(PREF_AUTODETECT_WARN, "Autodetect: Show Warning");
		createSettingMenuItem(PREF_DISABLE_DISCOURAGED_OPERATION, "Autodetect: Disable Discouraged Operations");
		new MenuItem(popupMenu, SWT.SEPARATOR);
	}

	private void createSettingMenuItem(final String prefKey, String message) {

		final MenuItem menuItem = new MenuItem(popupMenu, SWT.CHECK);
		menuItem.setText(message);
		menuItem.setSelection(prefIs(prefKey));
		menuItem.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				boolean sel = !prefIs(prefKey);
				menuItem.setSelection(sel);
				Activator.getDefault().getPreferenceStore().setValue(prefKey, sel);
				agent.fireEncodingChanged();
				if (sel && prefKey.equals(PREF_AUTODETECT_CHANGE)) {
					ActiveDocument doc = agent.getDocument();
					doc.infoMessage("'Set automatically' only applies when the file properties encoding is not set.");
				}
			}
		});
	}

	private void createShortcutMenu() {

		final ActiveDocument doc = agent.getDocument();

		// Workspace Preferences
		{
			MenuItem menuItem = new MenuItem(popupMenu, SWT.NONE);
			menuItem.setText(formatLabel("Workspace Preferences...", ResourcesPlugin.getEncoding()));
			menuItem.setImage(Activator.getImage("workspace"));
			menuItem.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					PreferencesUtil.createPreferenceDialogOn(Display.getDefault().getActiveShell(),
						"org.eclipse.ui.preferencePages.Workspace", null, null).open();
				}
			});
		}

		// Project Properties
		{
			final IProject project = doc.getProject();
			String encoding = null;
			if (project != null) {
				try {
					encoding = project.getDefaultCharset(false);
				} catch (CoreException e) {
					throw new IllegalStateException(e);
				}
				if (encoding == null) {
					encoding = "Inheritance";
				}
			}
			MenuItem menuItem = new MenuItem(popupMenu, SWT.NONE);
			menuItem.setText(formatLabel("Project Properties...", encoding));
			menuItem.setImage(Activator.getImage("project"));
			menuItem.setEnabled(project != null);
			menuItem.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					PreferencesUtil.createPropertyDialogOn(Display.getDefault().getActiveShell(),
						project,
						"org.eclipse.ui.propertypages.info.file", null, null).open();
				}
			});
		}

		// src/jar Package Root Properties
		final PackageRoot packageRoot = doc.getPackageRoot();
		if (packageRoot != null && packageRoot.element != null) {
			String encoding = packageRoot.encoding;
			if (encoding == null) {
				encoding = "Inheritance";
			}
			MenuItem menuItem = new MenuItem(popupMenu, SWT.NONE);
			menuItem.setText(formatLabel("Package Root Properties...", encoding));
			menuItem.setImage(Activator.getImage("root"));
			menuItem.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					PreferencesUtil.createPropertyDialogOn(Display.getDefault().getActiveShell(),
						packageRoot.element,
						"org.eclipse.jdt.ui.propertyPages.SourceAttachmentPage", null, null).open();
				}
			});
		}

		// File Properties
		{
			final IFile file = doc.getFile();
			String labelText = doc.getCurrentEncodingLabel();
			if (doc.getFilePropertiesEncoding() == null) {
				String currentEncoding = doc.getCurrentEncoding();
				if (Charsets.equals(currentEncoding, doc.getContentTypeEncoding())) {
					labelText = "Content Type";
				} else if (Charsets.equals(currentEncoding, doc.getContentCharset())) {
					labelText += format(" Content");
				} else {
					labelText = "Inheritance";
				}
			}
			MenuItem menuItem = new MenuItem(popupMenu, SWT.NONE);
			menuItem.setText(formatLabel("File Properties...", labelText));
			menuItem.setImage(Activator.getImage("file"));
			menuItem.setEnabled(file != null);
			menuItem.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					PreferencesUtil.createPropertyDialogOn(Display.getDefault().getActiveShell(),
						file,
						"org.eclipse.ui.propertypages.info.file", null, null).open();
				}
			});
		}

		// File Creation Preferences
		{
			creationEncoding = null;

			// Null if non plugin for editor
			IContentDescription contentDescription = doc.getContentDescription();
			if (contentDescription != null) {

				// Key: contentTypeId, Value: preferencePageId in corresponding plugin.xml
				Map<String, String> contentTypePrefMap = new HashMap<String, String>() {{
					put("org.eclipse.wst.json.core.jsonsource",	"org.eclipse.wst.json.ui.preferences.json.json");
					put("org.eclipse.wst.html.core.htmlsource",	"org.eclipse.wst.html.ui.preferences.html");
					put("org.eclipse.wst.css.core.csssource",	"org.eclipse.wst.css.ui.preferences.css");
					put("org.eclipse.wst.xml.core.xmlsource",		"org.eclipse.wst.xml.ui.preferences.xml.xml");
					put("org.eclipse.core.runtime.xml",				"org.eclipse.wst.xml.ui.preferences.xml.xml");
					put("org.eclipse.jst.jsp.core.jspsource",			"org.eclipse.jst.jsp.ui.preferences.jsp");
					put("org.eclipse.jst.jsp.core.cssjspsource",		"org.eclipse.jst.jsp.ui.preferences.jsp");
					put("org.eclipse.jst.jsp.core.cssjspfragmentsource","org.eclipse.jst.jsp.ui.preferences.jsp");
				}};
				final String preferencePageId = contentTypePrefMap.get(contentDescription.getContentType().getId());
				if (preferencePageId != null) {

					// Key: preferencePageId suffix, Value: encoding preference pluginId
					Map<String, String> creationEncodingMap = new HashMap<String, String>() {{
						put("json",	"org.eclipse.wst.json.core");
						put("html",	"org.eclipse.wst.html.core");
						put("css" ,	"org.eclipse.wst.css.core");
						put("xml" ,	"org.eclipse.wst.xml.core");
						put("jsp" ,	"org.eclipse.jst.jsp.core");
					}};
					String pluginId = creationEncodingMap.get(preferencePageId.replaceAll(".*\\.", ""));
					Preferences pref = InstanceScope.INSTANCE.getNode(pluginId);
					creationEncoding = pref.get("outputCodeset", "UTF-8");

					MenuItem menuItem = new MenuItem(popupMenu, SWT.NONE);
					menuItem.setText(formatLabel("File Creation Preferences...", creationEncoding));
					menuItem.setImage(Activator.getImage("file_new"));
					menuItem.addSelectionListener(new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent e) {
							PreferencesUtil.createPreferenceDialogOn(Display.getDefault().getActiveShell(),
								preferencePageId, null, null).open();
						}
					});
				}
			}
		}

		// Content Type Preferences
		{
			String encoding = doc.getContentTypeEncoding();
			if (encoding == null) {
				encoding = "Not Set";
			}
			MenuItem menuItem = new MenuItem(popupMenu, SWT.NONE);
			menuItem.setText(formatLabel("Content Types Preferences...", encoding));
			menuItem.setImage(Activator.getImage("content"));
			menuItem.setEnabled(doc.enabledContentType());
			menuItem.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {

					PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(
						Display.getDefault().getActiveShell(),
						"org.eclipse.ui.preferencePages.ContentTypes", null, null);
					try {
						Field field = ContentTypesPreferencePage.class.getDeclaredField("contentTypesViewer");
						field.setAccessible(true);
						TreeViewer contentTypesViewer = (TreeViewer) field.get(dialog.getSelectedPage());

						IContentDescription contentDescription = doc.getContentDescription();
						if (contentDescription != null) {
							IContentType contentType = contentDescription.getContentType();
							contentTypesViewer.setSelection(new StructuredSelection(contentType), true);
						}
					}
					catch (Exception ex) {
						Activator.warn("Failed select contentTypesViewer item.", ex);
					}
					dialog.open();
				}
			});
		}
		new MenuItem(popupMenu, SWT.SEPARATOR);
	}

	private void createSelectionMenu() {

		final ActiveDocument doc = agent.getDocument();
		final List<EncodingItem> encodingItemList = getEncodingItemList(doc);
		boolean nonDirty = !agent.isDocumentDirty() && doc.canChangeFileEncoding();

		// Add/Remove Bom
		if (doc.isUTFEncoding()) {
			if (doc.hasBOM()) {
				MenuItem menuItem = new MenuItem(popupMenu, SWT.NONE);
				menuItem.setText("Remove BOM");
				menuItem.setImage(Activator.getImage("bom_remove"));
				menuItem.setEnabled(nonDirty);
				menuItem.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						doc.removeBOM();
					}
				});
			} else if (doc.canAddBOM()) {
				MenuItem menuItem = new MenuItem(popupMenu, SWT.NONE);
				menuItem.setText("Add BOM");
				menuItem.setImage(Activator.getImage("bom_add"));
				menuItem.setEnabled(nonDirty && !prefIs(PREF_DISABLE_DISCOURAGED_OPERATION));
				menuItem.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						doc.addBOM();
					}
				});
			}
		}

		// Convert Charset
		{
			MenuItem menuItem = new MenuItem(popupMenu, SWT.CASCADE);
			menuItem.setText(format("Convert Charset %s to", doc.getCurrentEncoding()));
			menuItem.setImage(Activator.getImage("charset_convert"));
			menuItem.setEnabled(nonDirty);
			if (prefIs(PREF_DISABLE_DISCOURAGED_OPERATION) &&
					(doc.getDetectedCharset() == null || doc.mismatchesEncoding())) {
				menuItem.setEnabled(false);
			}
			Menu convertMenu = new Menu(menuItem);
			menuItem.setMenu(convertMenu);

			for (final EncodingItem ei : encodingItemList) {

				if (Charsets.equals(ei.encoding, doc.getCurrentEncoding())) {
					continue;
				}
				MenuItem mItem = new MenuItem(convertMenu, SWT.NONE);
				mItem.setText(ei.menuText);
				mItem.setImage(Charsets.getImage(ei.encoding));
				mItem.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						doc.convertCharset(ei.encoding);
					}
				});
			}
		}

		// Change Encoding
		{
			MenuItem menuItem = new MenuItem(popupMenu, SWT.CASCADE);
			menuItem.setText("Change Encoding to");
			menuItem.setImage(Activator.getImage("encoding_change"));
			menuItem.setEnabled(nonDirty);
			if (prefIs(PREF_DISABLE_DISCOURAGED_OPERATION) && doc.matchesEncoding()) {
				menuItem.setEnabled(false);
			}
			Menu encodingMenu = new Menu(menuItem);
			menuItem.setMenu(encodingMenu);

			for (final EncodingItem ei : encodingItemList) {

				final MenuItem mItem = new MenuItem(encodingMenu, SWT.RADIO);
				mItem.setText(ei.menuText);
				mItem.setImage(Charsets.getImage(ei.encoding));
				if (Charsets.equals(ei.encoding, doc.getCurrentEncoding())) {
					mItem.setSelection(true);
				}
				// Converted to one line and freeze on big file
				else if (prefIs(PREF_DISABLE_DISCOURAGED_OPERATION) && ei.encoding.startsWith("UTF-16")) {
					mItem.setEnabled(false);
				}
				mItem.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						if (mItem.getSelection()) {
							doc.setEncoding(ei.encoding);
						}
					}
				});
			}
		}

		// Change encoding for Autodetect
		{
			final MenuItem menuItem = new MenuItem(popupMenu, SWT.NONE);
			menuItem.setImage(Activator.getImage("autodetect"));

			if (doc.getDetectedCharset() == null) {
				menuItem.setText("Change Encoding (Cannot Autodetect)");
				menuItem.setEnabled(false);
			}
			else if (doc.matchesEncoding()) {
				menuItem.setText("Change Encoding (Matches Autodetect)");
				menuItem.setEnabled(false);
			}
			else {
				menuItem.setText(format("Change Encoding to %s (Autodetect)", doc.getDetectedCharset()));
				menuItem.setEnabled(nonDirty);
				menuItem.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						for (EncodingItem i : encodingItemList) {
							if (Charsets.equals(i.encoding, doc.getDetectedCharset())) {
								doc.setEncoding(i.encoding);
								break;
							}
						}
					}
				});
			}
		}
	}

	@SuppressWarnings("unchecked")
	private List<EncodingItem> getEncodingItemList(final ActiveDocument doc) {

		List<String> encodingList = IDEEncoding.getIDEEncodings();
		Charsets.add(encodingList, doc.getCurrentEncoding());
		Charsets.add(encodingList, doc.getInheritedEncoding());
		Charsets.add(encodingList, doc.getContentTypeEncoding());
		Charsets.add(encodingList, doc.getContentCharset());
		Charsets.add(encodingList, doc.getDetectedCharset());
		Charsets.add(encodingList, creationEncoding);

		final List<EncodingItem> encodingItemList = new ArrayList<EncodingItem>();
		for (final String encoding : encodingList) {

			EncodingItem i = new EncodingItem();
			encodingItemList.add(i);
			i.encoding = encoding;

			List<String> noteList = new ArrayList<String>() {
				{
					add(Charsets.equals(encoding, doc.getContentCharset()), "Content");
					add(Charsets.equals(encoding, doc.getContentTypeEncoding()), "Content Type");
					add(Charsets.equals(encoding, doc.getInheritedEncoding()), "Inheritance");
					add(Charsets.equals(encoding, doc.getDetectedCharset()), "Autodetect");
					add(Charsets.equals(encoding, creationEncoding), "Creation");
				}
				public void add(boolean enable, String text) {
					if (enable) {
						super.add(text);
					}
				}
			};
			i.menuText = formatLabel(i.encoding, noteList);
		}
		return encodingItemList;
	}
}
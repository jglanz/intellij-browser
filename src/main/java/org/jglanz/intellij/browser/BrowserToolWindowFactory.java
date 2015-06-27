

package org.jglanz.intellij.browser;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.teamdev.jxbrowser.chromium.Browser;
import com.teamdev.jxbrowser.chromium.events.StatusEvent;
import com.teamdev.jxbrowser.chromium.events.StatusListener;
import com.teamdev.jxbrowser.chromium.swing.BrowserView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Created by jglanz on 6/26/15.
 */

public class BrowserToolWindowFactory implements ToolWindowFactory {

	private JPanel browserToolWindowContent;
	private JTextField addressField;

	public BrowserToolWindowFactory() {

	}

	// Create the tool window content.
	public void createToolWindowContent(Project project, ToolWindow toolWindow) {

		final Browser browser = new Browser();
		final BrowserView browserView = new BrowserView(browser);

		browserToolWindowContent.add(browserView);


		ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
		Content content = contentFactory.createContent(browserToolWindowContent, "", false);
		toolWindow.getContentManager().addContent(content, BorderLayout.CENTER);

		browserView.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				browser.setSize(browserView.getSize());
			}
		});

		addressField.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				browser.loadURL(addressField.getText());
			}
		});



		browser.addStatusListener(new StatusListener() {
			@Override
			public void onStatusChange(StatusEvent statusEvent) {
				addressField.setText(browser.getURL());
			}
		});
		browser.loadURL("http://google.com");




	}






}
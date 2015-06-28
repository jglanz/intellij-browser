

/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Jonathan Glanz
 *
 *                            Permission is hereby granted, free of charge, to any person obtaining a copy
 *                            of this software and associated documentation files (the "Software"), to deal
 *                            in the Software without restriction, including without limitation the rights
 *                            to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *                            copies of the Software, and to permit persons to whom the Software is
 *                            furnished to do so, subject to the following conditions:
 *
 *                            The above copyright notice and this permission notice shall be included in all
 *                            copies or substantial portions of the Software.
 *
 *                            THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *                            IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *                            FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *                            AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *                            LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *                            OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *                            SOFTWARE.
 *
 */

package org.jglanz.intellij.browser;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.tools.ToolManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener
import groovy.swing.SwingBuilder
import groovy.util.logging.Slf4j;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.web.WebView
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Created by jglanz on 6/26/15.
 */
@Slf4j
class BrowserToolWindowFactory implements ToolWindowFactory {



	private JPanel browserToolWindowContent;
	private JTextField addressField;

	private JFXPanel fxPanel

	WebView webView

	public BrowserToolWindowFactory() {

	}

	// Create the tool window content.
	public void createToolWindowContent(Project project, ToolWindow toolWindow) {

		println "Creating browser"

		def swing = new SwingBuilder()

		fxPanel = new JFXPanel()

		addressField = swing.textField()
		browserToolWindowContent = swing.panel() {
			borderLayout()

			panel(constraints: BorderLayout.NORTH) {
				borderLayout()

				widget(addressField, constraints: BorderLayout.CENTER)
			}

			widget(fxPanel, constraints: BorderLayout.CENTER)

		}


		ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
		Content content = contentFactory.createContent(browserToolWindowContent, "", false);
		toolWindow.getContentManager().addContent(content, BorderLayout.CENTER);


		Platform.setImplicitExit(false)
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				initBrowser();
			}
		})

	}

	void initBrowser() {
		println "Making browser"

		webView = new WebView();
		Scene scene = new Scene(webView);

		fxPanel.setScene(scene);

		webView.getEngine().load("http://www.google.com");



		webView.engine.onStatusChanged = { e ->
			ApplicationManager.application.invokeLater({
				addressField.text = webView.engine.location
			} as Runnable)

		}

		addressField.actionPerformed = {
			Platform.runLater({webView.getEngine().load(addressField.text)} as Runnable)

		}
	}






}
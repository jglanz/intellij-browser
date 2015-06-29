

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
import groovy.beans.Bindable
import groovy.swing.SwingBuilder
import groovy.util.logging.Slf4j;
import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Group;
import javafx.scene.Scene
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView
import org.jdesktop.swingx.JXPanel
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
	WebEngine webEngine

	UIState uiState = new UIState()

	@Bindable
	static class UIState {
		boolean findVisible = false
	}

	public BrowserToolWindowFactory() {

	}

	// Create the tool window content.
	public void createToolWindowContent(Project project, ToolWindow toolWindow) {

		println "Creating browser"

		def swing = new SwingBuilder()

		fxPanel = new JFXPanel()
		fxPanel.keyPressed = { KeyEvent e ->
			if (e.modifiers & KeyEvent.VK_META && e.keyChar == 'f') {
				println "Cmd f pressed"

				uiState.findVisible = !uiState.findVisible
			}
		}

		addressField = swing.textField()
		browserToolWindowContent = swing.panel() {

			def findPanel, findField
			def addTab

			def executeFind = {
				highlight(findField.text)
			}

			borderLayout()

			vbox(constraints: BorderLayout.NORTH) {

				//Address Panel
				panel() {
					borderLayout()
					widget(addressField, constraints: BorderLayout.CENTER)
				}

				// Find panel
				findPanel = panel(visible: bind(source: uiState, sourceProperty: "findVisible")) {
					borderLayout()
					findField = textField(constraints: BorderLayout.CENTER, actionPerformed: executeFind)

					button(text: "Find", constraints: BorderLayout.EAST, actionPerformed: executeFind)
				}

			}

			panel() {
				borderLayout()
				tabbedPane(constraints: BorderLayout.CENTER) {

					addTab = { String name, String location ->


						JFXPanel fx = new JFXPanel()
						widget(fx, title: name)

						onPlatform {
							WebView wv = new WebView();
							WebEngine we = wv.engine

							Scene scene = new Scene(wv);
							fx.setScene(scene);

							we.load(location)

						}

						fx
					}

					addTab("ferrari", "http://www.ferrari.com")
					addTab("google", "http://www.google.com")
				}


			}



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
		webEngine = webView.engine

		Scene scene = new Scene(webView);
		fxPanel.setScene(scene);
		webView.getEngine().load("http://www.google.com")

		String lastSetLocation = null

		webEngine.loadWorker.stateProperty().addListener(new ChangeListener<Worker.State>() {
			@Override
			void changed(ObservableValue<? extends Worker.State> observable, Worker.State oldValue, Worker.State newValue) {
				if (newValue.equals(Worker.State.SUCCEEDED)) {
					if (webEngine.loadWorker.state == Worker.State.SUCCEEDED) {
						println "Loading libs for ${webEngine.location}"
						ensureLibs()

					}
				}
			}
		});

		webView.engine.onStatusChanged = { e ->
			ApplicationManager.application.invokeLater({
				if (!(webView.engine.location in [addressField.text, lastSetLocation])) {
					lastSetLocation = addressField.text = webView.engine.location
				}



			} as Runnable)

		}

		addressField.actionPerformed = {
			Platform.runLater({webView.getEngine().load(addressField.text)} as Runnable)

		}
	}

	void onPlatform(Closure closure) {
		Platform.runLater(closure as Runnable)
	}

	void ensureLibs() {
		onPlatform() {
			String script = BrowserToolWindowFactory.class.getResource("/js/libs.js").text
			webEngine.executeScript("""
	(function() {

		${script};

		var style = \$("<style>.highlight { background-color: yellow }</style>");
		\$("head").append(style);


	})();
	""")
		}
	}


	void highlight(String text) {
		onPlatform() {
			webEngine.executeScript("""
var body = \$("body");
body.removeHighlight();
body.highlight("${text}");

""")
		}
	}






}
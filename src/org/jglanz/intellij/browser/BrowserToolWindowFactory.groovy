

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

package org.jglanz.intellij.browser

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.tools.ToolManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener
import groovy.beans.Bindable
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.swing.SwingBuilder
import groovy.transform.ToString
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
import java.awt.event.*
import java.beans.PropertyChangeEvent;

/**
 * Created by jglanz on 6/26/15.
 */
@Slf4j
class BrowserToolWindowFactory implements ToolWindowFactory, IdeEventQueue.EventDispatcher, Disposable, FocusListener {

	static {
		Platform.setImplicitExit(false)
	}

	@ToString
	static class TabManager {
		def all = []
		int index = 0
		BrowserTab current
	}

	private JPanel browserToolWindowContent;
	private JTextField addressField;

	private JFXPanel fxPanel

	JTabbedPane tabbedPane

	UIState uiState = new UIState()

	TabManager tabs = new TabManager()

	File configFile = new File(System.getProperty('user.home'), ".intelli-browser-config.json")

	def swing = new SwingBuilder()

	JPanel bookmarkPanel

	def config

	def addTab, closeTab
	JTextField findField


	@Bindable
	static class UIState {
		boolean findVisible = false
	}

	@Override
	void dispose() {

	}

	@Override
	boolean dispatch(AWTEvent e) {
		if (e instanceof KeyEvent) {
			return handleKeyEvent(e)
		}

		return false
	}

	void installKeyDispatcher() {
		IdeEventQueue.getInstance().addDispatcher(this, this);
	}

	void uninstallKeyDispatcher() {
		IdeEventQueue.getInstance().removeDispatcher(this);

	}

	@Override
	void focusGained(FocusEvent e) {
		//installKeyDispatcher()
	}

	@Override
	void focusLost(FocusEvent e) {
		//uninstallKeyDispatcher()
	}

	boolean metaDown = false

	boolean handleKeyEvent(KeyEvent e) {
		boolean match = false
		boolean ancestor = browserToolWindowContent.isAncestorOf(e.component) || browserToolWindowContent == e.component ||
			browserToolWindowContent.isAncestorOf((Component) e.source)

//		println "Key pressed ${ancestor} ${e.consumed} ${e.keyChar} ${e.modifiers} ${e.modifiersEx} ${e.modifiers & KeyEvent.VK_META}  ${e.modifiers & KeyEvent.VK_CONTROL}"
//		println "Meta down before ${metaDown}"
		if ((ancestor || metaDown) && e.getID() == KeyEvent.KEY_RELEASED && (e.modifiers & KeyEvent.VK_META) != 0 && !e.consumed) {

			switch(e.keyChar) {
				case 'f':

					println "Cmd f pressed"
					uiState.findVisible = !uiState.findVisible
					findField.requestFocus()
					match = true
					break
				case 't':
					println "Cmd t - add tab"
					addTab("http://www.google.com")
					match = true
					break
				case 'w':
					println "Cmd w - close tab"
					if (tabs.all.size() < 2 || !tabs.current) {
						println "There is only 1 tab or not a current tab ${tabs}"
						match = true
						break
					}

					closeTab(-1)
					match = true
					break
				case 'd':
					createBookmark()
					match = true
					break;
			}


		}

		if (!metaDown || (e.getID() == KeyEvent.KEY_RELEASED && e.keyCode == KeyEvent.VK_META)) {
			metaDown = !e.consumed && (e.metaDown || (e.modifiers & KeyEvent.VK_META) > 0) && ancestor

		}

		if (match || metaDown) {
			e.consume()
			match = true
		}
		match
	}

	void loadConfig() {
		println "Loading config (${configFile.absolutePath}) - ${configFile.exists()}"
		if (configFile.exists()) {
			try {
				String jsonText = configFile.text
				println "Read config ${jsonText}"

				config = new JsonSlurper().parse(new StringReader(jsonText))
			} catch (Exception e) {
				println "Failed to load config file ${e}"
			}
		}

		config = config ?: [:]
		if (!config.bookmarks) {
			config.bookmarks = [:]
		}
	}

	void saveConfig() {
		println "Saving config"
		configFile.text = JsonOutput.toJson(config)
	}

	public BrowserToolWindowFactory() {
		loadConfig()
	}

	// Create the tool window content.
	public void createToolWindowContent(Project project, ToolWindow toolWindow) {

//		IdeEventQueue.instance.addDispatcher(this, this)
		installKeyDispatcher()


		println "Creating browser"

		initUI()

		browserToolWindowContent.setFocusable(true)
		browserToolWindowContent.addFocusListener(this)

		ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
		Content content = contentFactory.createContent(browserToolWindowContent, "", false);
		toolWindow.getContentManager().addContent(content, BorderLayout.CENTER);





	}

	void createBookmark() {
		if (tabs.current) {
			def bookmark = [
				name: tabs.current.tabState.title,
				location: tabs.current.tabState.location
			]

			config.bookmarks[bookmark.name] = bookmark
			saveConfig()

			renderBookmarks()
		}

	}


	void initUI() {




		def superKeyHandler = { KeyEvent e ->
			handleKeyEvent(e)
		}

		//Create required global variables/closures
		def findPanel


		def executeFind = {
			tabs.current?.highlight(findField.text)
		}

		closeTab = { int index ->
			if (tabs.all.size() == 1) {
				println "Can not remove last tab"
				return
			}

			if (index == -1) {
				index = tabs.index
			}

			tabs.all.remove(index)
			tabbedPane.remove(index)

			index--
			if (index < 0)
				index = 0

			tabs.current = tabs.all[index]
			tabs.index = index

		}



		addTab = { String location ->
			def tab = new BrowserTab(location, uiState)
			tabs.all << tab
			if (!tabs.current)
				tabs.current = tab

			BrowserTab.TabState tabState = tab.tabState
			tab.panel.keyPressed = superKeyHandler

			tabbedPane.addTab(location, tab.panel)

			def findTabIndex = { targetState ->
				for (int i = 0; i < tabs.all.size();i++) {
					if (tabs.all[i].tabState == targetState)
						return i
				}

				return -1
			}

			tabState.propertyChange = { PropertyChangeEvent e ->
				println "Tab prop change ${e.propertyName} ${e.newValue} ${e.source}"
				SwingUtilities.invokeLater( {
					switch (e.propertyName) {
						case "title":
							def tabIndex = findTabIndex(e.source)
							if (tabIndex > -1)
								tabbedPane.setTitleAt(tabIndex, (String) e.newValue)
							break;
						case "location":
							if (tabs.current?.tabState == e.source) {
								addressField.text = e.newValue
							}
					}
				} as Runnable)



			}


		}



		addressField = swing.textField()
		browserToolWindowContent = swing.panel() {
			//Set the layout manager
			borderLayout()


			vbox(constraints: BorderLayout.NORTH) {

				//Address Panel
				panel() {
					borderLayout()
					widget(addressField, constraints: BorderLayout.CENTER)
					addressField.actionPerformed = {
						tabs.current?.load(addressField.text)
					}
				}

				// Find panel
				findPanel = panel(visible: bind(source: uiState, sourceProperty: "findVisible")) {
					borderLayout()
					findField = textField(constraints: BorderLayout.CENTER, actionPerformed: executeFind, keyPressed: superKeyHandler)

					button(text: "Find", constraints: BorderLayout.EAST, actionPerformed: executeFind)
				}

				bookmarkPanel = panel(visible: config.bookmarks.size() > 0) {
					flowLayout()

				}



			}

			panel() {
				borderLayout()
				tabbedPane = tabbedPane(constraints: BorderLayout.CENTER, keyPressed: superKeyHandler) {


				}

				tabbedPane.stateChanged = { e ->
					println "Tab state changed"
					if (tabbedPane.selectedIndex != tabs.index) {
						tabs.index = tabbedPane.selectedIndex
						tabs.current = tabs.all[tabs.index]
						addressField.text = tabs.current.tabState.location
					}

				}

			}

		}



		browserToolWindowContent.keyPressed = superKeyHandler

		browserToolWindowContent.setFocusable(true);
		browserToolWindowContent.requestFocusInWindow();

		addTab("http://www.google.com")
		renderBookmarks()

	}

	void renderBookmarks() {

		SwingUtilities.invokeLater({
			bookmarkPanel.visible = config.bookmarks.size() > 0
			bookmarkPanel.removeAll()
			config.bookmarks.each { k, v ->
				bookmarkPanel.add(
					swing.button(text: k, actionPerformed: { e ->
						tabs.current?.load(v.location)
					}, mouseReleased: { MouseEvent e ->
						if ((e.modifiers & MouseEvent.BUTTON3_MASK) != 0) {
							println "Command held - deleting"
							config.bookmarks.remove(k)
							saveConfig()
							renderBookmarks()
							e.consume()
						}
					})
				)
			}

			bookmarkPanel.revalidate()
			bookmarkPanel.repaint()
		} as Runnable)
	}

	void standalone() {
		initUI()

		swing.edt {
			frame(title: 'Frame', size: [600, 600], show: true, defaultCloseOperation:JFrame.EXIT_ON_CLOSE) {
				borderLayout()
				widget(browserToolWindowContent, constraints: BorderLayout.CENTER)
			}
		}
	}

	static void main(String[] args) {

		new BrowserToolWindowFactory().standalone()
	}








}
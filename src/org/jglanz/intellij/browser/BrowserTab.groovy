package org.jglanz.intellij.browser

import com.intellij.openapi.application.ApplicationManager
import groovy.beans.Bindable
import groovy.util.logging.Slf4j
import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import org.jglanz.intellij.browser.BrowserToolWindowFactory.UIState

import java.beans.PropertyChangeEvent

/**
 * Created by jglanz on 6/29/15.
 */

@Slf4j
class BrowserTab implements ChangeListener<Worker.State>{

	@Bindable
	static class TabState {
		boolean visible
		String location
		String lastLocation
		String title = null
	}


	WebView view
	WebEngine engine
	JFXPanel panel
	TabState tabState = new TabState()
	UIState uiState

	BrowserTab(String location, UIState uiState) {
		this.uiState = uiState



		panel = new JFXPanel()

		onPlatform {
			view = new WebView()
			engine = view.engine
			Scene scene = new Scene(view);
			panel.setScene(scene);

			tabState.propertyChange = { PropertyChangeEvent e ->
				if (e.propertyName == "location" && e.newValue != tabState.lastLocation) {
					engine.load(tabState.lastLocation = (String) e.newValue)
				}
			}

			engine.titleProperty().addListener({ obj, oldValue, newValue ->
				if (newValue != tabState.title)
					tabState.title = newValue

			} as ChangeListener<String>)
			engine.loadWorker.stateProperty().addListener(this)

			engine.onStatusChanged = { e ->
				if (!(engine.location in [tabState.lastLocation])) {
					tabState.lastLocation = tabState.location = engine.location
				}
			}

			engine.load("http://www.google.com")
		}

	}

	static void onPlatform(Closure closure) {
		Platform.runLater(closure as Runnable)
	}

	void load(String url) {
		onPlatform {
			engine.load(url)
		}
	}

	void ensureLibs() {
		onPlatform() {
			String script = BrowserToolWindowFactory.class.getResource("/js/libs.js").text
			engine.executeScript("""
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
			engine.executeScript("""
var body = \$("body");
body.removeHighlight();
body.highlight("${text}");
\$('html, body').animate({
        scrollTop: \$(".highlight").offset().top
    }, 250);
""")
		}
	}



	@Override
	void changed(ObservableValue<? extends Worker.State> observable, Worker.State oldValue, Worker.State newValue) {
		if (newValue.equals(Worker.State.SUCCEEDED)) {

			if (engine.loadWorker.state == Worker.State.SUCCEEDED) {
				println "Loading libs for ${engine.location}"
				ensureLibs()

			}
		}
	}

}

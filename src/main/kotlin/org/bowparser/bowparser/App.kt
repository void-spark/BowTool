package org.bowparser.bowparser

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import javafx.application.Application
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.transformation.FilteredList
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.stage.FileChooser
import javafx.stage.Stage
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.function.Predicate


@OptIn(ExperimentalUnsignedTypes::class)
class App : Application() {

// Fake python scripts can output data to stdout, and let Java decode it.


    override fun start(stage: Stage) {
        val table = TableView(FXCollections.emptyObservableList<Message>())


        val mapper = ObjectMapper(YAMLFactory())
        mapper.registerModule(KotlinModule.Builder().build())


        val appConfigPath = Paths.get("app", "config.yml")
        val localConfigPath = Paths.get("config.yml")
        val configPath = if(Files.exists(appConfigPath)) appConfigPath else localConfigPath

        val config = Files.newBufferedReader(configPath).use {
            mapper.readValue(it, Config::class.java)
        }
        println(config.toString())

        val typesByInt = byInt(config.types)
        val deviceByInt = byInt(config.devices)
        val dataIdsByInt = byInt(config.dataIds)
        val commandsByInt = byInt(config.commands)

        val decoder = Decoder(commandsByInt, dataIdsByInt)

        stage.title = "BOW decoder"

        val fileChooser = FileChooser()

        val openBinaryButton = Button("Open binary...")
        val openHexButton = Button("Open hex...")

        val label = Label("BOW decoder")
        label.font = Font("Arial", 20.0)

        table.columns.addAll(
            col("message", 280.0) { msg -> hex(msg.message) },
            col("type", 110.0) { msg -> withName(msg.type, typesByInt) },
            col("target", 60.0) { msg -> withName(msg.target, deviceByInt) },
            col("source", 60.0) { msg -> withName(msg.source, deviceByInt) },
            col("size", 50.0) { msg -> "${msg.size}" },
            col("decoded", 800.0) { msg -> decoder.decode(msg) }
        )


        val buttonBox = HBox()
        //buttonBox.padding = Insets(15.0, 12.0, 15.0, 12.0)
        buttonBox.spacing = 10.0
        buttonBox.background = Background(BackgroundFill(Color.STEELBLUE, CornerRadii.EMPTY, Insets.EMPTY))
        buttonBox.children.addAll(openBinaryButton, openHexButton)

        val hbox = HBox()
        hbox.padding = Insets(15.0, 12.0, 15.0, 12.0)
        hbox.spacing = 10.0
        hbox.background = Background(BackgroundFill(Color.STEELBLUE, CornerRadii.EMPTY, Insets.EMPTY))

        val handoff = CheckBox("HANDOFF")
        val ping = CheckBox("PING")
        val buttonCheck = CheckBox("Button Check")

        val motorCheck = CheckBox("Motor")
        val displayCheck = CheckBox("Display")
        val batteryCheck = CheckBox("Battery")

        hbox.children.addAll(handoff, ping, buttonCheck, motorCheck, displayCheck, batteryCheck)

        val vbox = VBox()
        vbox.children.addAll(label, buttonBox, hbox)


        val pane = BorderPane()

        table.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE)

//        pane.spacing = 5.0
        pane.padding = Insets(10.0, 0.0, 0.0, 10.0)
//        pane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE)
        pane.top = vbox
        pane.center = table


        motorCheck.isSelected = true
        displayCheck.isSelected = true
        batteryCheck.isSelected = true

        val filterPredicate = Bindings.createObjectBinding({
            Predicate<Message> { message ->
                handoff.isSelected || !message.isHandoff()
            }.and { message ->
                ping.isSelected || !message.isPingOrPong()
            }.and { message ->
                buttonCheck.isSelected || !message.isCmd(0x22)
            }.and { message ->
                motorCheck.isSelected || !(message.tgt() == 0x00 || message.src() == 0x00)
            }.and { message ->
                displayCheck.isSelected || !(message.tgt() == 0x0c || message.src() == 0x0c)
            }.and { message ->
                batteryCheck.isSelected || !(message.tgt() == 0x02 || message.src() == 0x02)
            }

        }, handoff.selectedProperty(), ping.selectedProperty(), buttonCheck.selectedProperty(), motorCheck.selectedProperty(), displayCheck.selectedProperty(), batteryCheck.selectedProperty())

        openBinaryButton.setOnAction { event ->
            val file = fileChooser.showOpenDialog(stage)
            if (file != null) {
                val task = FileReaderTask(file.toPath(), true, deviceByInt, decoder)
                val filter = FilteredList(task.getMessages())
                filter.predicateProperty().bind(filterPredicate)
                table.itemsProperty().set(filter)

                Thread(task).start()
            }
        }

        openHexButton.setOnAction { event ->
            val file = fileChooser.showOpenDialog(stage)
            if (file != null) {
                val task = FileReaderTask(file.toPath(), false, deviceByInt, decoder)
                val filter = FilteredList(task.getMessages())
                filter.predicateProperty().bind(filterPredicate)
                table.itemsProperty().set(filter)

                Thread(task).start()
            }
        }

        stage.scene = Scene(pane)
        stage.show()


        // Serial reader ? (add items on the fly?)
        // Add items on the fly? (how to do background tasks anyways?) And bootup background tasks?
        // Req/Resp are a pair, if we find a set decode together
        // Load file dialog of course
        // Caching of decoded messages? Most repeat. Key might be pair of messages


    }

    private fun col(header: String, width: Double, formatter: (Message) -> String): TableColumn<Message, String> {
        val col = TableColumn<Message, String>(header)
        col.prefWidth = width
        col.setCellValueFactory { SimpleStringProperty(formatter.invoke(it.value)) }
        return col
    }
}

fun main(args: Array<String>) {
    Application.launch(App::class.java, *args)
}
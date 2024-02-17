package org.bowparser.bowparser

import com.fazecast.jSerialComm.SerialPort
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
import javafx.util.StringConverter
import java.util.function.Predicate


@OptIn(ExperimentalUnsignedTypes::class)
class App : Application() {

// Fake python scripts can output data to stdout, and let Java decode it.


    override fun start(stage: Stage) {
        val table = TableView(FXCollections.emptyObservableList<Message>())

        val config = loadConfig()

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

        val portCombBox = ComboBox(FXCollections.observableArrayList(SerialPort.getCommPorts().toList()))
        val baudComboBox = ComboBox(FXCollections.observableArrayList(9600, 19200))

        portCombBox.setConverter(object : StringConverter<SerialPort>() {
            override fun toString(port: SerialPort?): String? {
                return if (port == null) "-" else port.systemPortName
            }

            override fun fromString(string: String?): SerialPort? {
                return null
            }
        })

        portCombBox.selectionModel.select(0)
        baudComboBox.selectionModel.select(0)

        val scanMotor = Button("Scan motor")
        val scanBattery = Button("Scan battery")
        val scanCU3 = Button("Scan CU3")
        val pairDisplay = Button("Pair display")
        val pairBattery = Button("Pair battery")

        val label = Label("BOW decoder")
        label.font = Font("Arial", 20.0)

        table.columns.addAll(
            col("message", 280.0) { msg -> hex(msg.message) },
            col("type", 110.0) { msg -> withName(msg.type, typesByInt) },
            col("target", 60.0) { msg -> withName(msg.target, deviceByInt) },
            col("source", 60.0) { msg -> withName(msg.source, deviceByInt) },
            col("size", 50.0) { msg -> "${msg.size}" },
            col("decoded", 1000.0) { msg -> if (decoder.check(msg).isEmpty()) decoder.decode(msg) else decoder.check(msg) }
        )


        val buttonBox = HBox()
        //buttonBox.padding = Insets(15.0, 12.0, 15.0, 12.0)
        buttonBox.spacing = 10.0
        buttonBox.background = Background(BackgroundFill(Color.STEELBLUE, CornerRadii.EMPTY, Insets.EMPTY))
        buttonBox.children.addAll(openBinaryButton, openHexButton, portCombBox, baudComboBox, scanMotor, scanBattery, scanCU3, pairDisplay, pairBattery)


        val handoff = CheckBox("HANDOFF")
        val ping = CheckBox("PING")
        val buttonCheck = CheckBox("Button Check")

        val displayUpdate = CheckBox("Display update")
        val invalid = CheckBox("Invalid")

        val motorCheck = CheckBox("Motor")
        val displayCheck = CheckBox("Display")
        val batteryCheck = CheckBox("Battery")
        val pcCheck = CheckBox("PC")

        val getData = CheckBox("GET DATA")
        val putData = CheckBox("PUT DATA")

        val hbox = HBox()
        hbox.padding = Insets(15.0, 12.0, 15.0, 12.0)
        hbox.spacing = 10.0
        hbox.background = Background(BackgroundFill(Color.STEELBLUE, CornerRadii.EMPTY, Insets.EMPTY))

        val hbox2 = HBox()
        hbox2.padding = Insets(15.0, 12.0, 15.0, 12.0)
        hbox2.spacing = 10.0
        hbox2.background = Background(BackgroundFill(Color.STEELBLUE, CornerRadii.EMPTY, Insets.EMPTY))


        val label2 = Label("Show")
        label2.font = Font(label2.font.size * 1.5)

        hbox.children.addAll(label2, handoff, ping, buttonCheck, displayUpdate, invalid, motorCheck, displayCheck, batteryCheck, pcCheck)

        val label3 = Label("Show only")
        label3.font = Font(label3.font.size * 1.5)
        hbox2.children.addAll(label3, getData, putData)

        val vbox = VBox()
        vbox.children.addAll(label, buttonBox, hbox, hbox2)


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
        pcCheck.isSelected = true
        displayUpdate.isSelected = true
        invalid.isSelected = true

        val filterPredicate = Bindings.createObjectBinding(
            {
                Predicate<Message> { message ->
                    handoff.isSelected || !message.isHandoff()
                }.and { message ->
                    ping.isSelected || !message.isPingOrPong()
                }.and { message ->
                    buttonCheck.isSelected || !message.isCmd(0x22)
                }.and { message ->
                    displayUpdate.isSelected || !(message.isCmd(0x26) || message.isCmd(0x27) || message.isCmd(0x28))
                }.and { message ->
                    invalid.isSelected || decoder.check(message).isEmpty()
                }.and { message ->
                    motorCheck.isSelected || !(message.tgt() == 0x00 || message.src() == 0x00)
                }.and { message ->
                    displayCheck.isSelected || !(message.tgt() == 0x0c || message.src() == 0x0c)
                }.and { message ->
                    batteryCheck.isSelected || !(message.tgt() == 0x02 || message.src() == 0x02)
                }.and { message ->
                    pcCheck.isSelected || !(message.tgt() == 0x04 || message.src() == 0x04)
                }.and { message ->
                    !getData.isSelected || message.isCmd(0x08)
                }.and { message ->
                    !putData.isSelected || message.isCmd(0x09)
                }
            },
            handoff.selectedProperty(),
            ping.selectedProperty(),
            buttonCheck.selectedProperty(),
            displayUpdate.selectedProperty(),
            invalid.selectedProperty(),
            motorCheck.selectedProperty(),
            displayCheck.selectedProperty(),
            batteryCheck.selectedProperty(),
            pcCheck.selectedProperty(),
            getData.selectedProperty(),
            putData.selectedProperty()
        )

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

        scanMotor.setOnAction { event -> Scanner(portCombBox.value, baudComboBox.value, 0x00u, dataIdsByInt).exec() }
        scanBattery.setOnAction { event -> Scanner(portCombBox.value, baudComboBox.value, 0x02u, dataIdsByInt).exec() }
        scanCU3.setOnAction { event -> Scanner(portCombBox.value, baudComboBox.value, 0x0Cu, dataIdsByInt).exec() }
        pairDisplay.setOnAction { event -> DisplayPairer(portCombBox.value, baudComboBox.value, 1).exec() }
        pairBattery.setOnAction { event -> BatteryPairer(portCombBox.value, baudComboBox.value).exec() }

        stage.scene = Scene(pane)
        stage.show()


        // Serial reader ? (add items on the fly?)
        // Add items on the fly? (how to do background tasks anyways?) And bootup background tasks?
        // Req/Resp are a pair, if we find a set decode together
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
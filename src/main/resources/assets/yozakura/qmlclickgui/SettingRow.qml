import "."

Rectangle {
    id: row
    property string moduleName: ""
    property string settingName: ""
    property string settingTitle: ""
    property string settingType: "text"
    property var currentValue: ""
    property real ratio: 0
    property var swatches: ["#E98BC1", "#F472B6", "#A78BFA", "#60A5FA", "#22D3EE", "#34D399", "#FBBF24", "#F87171", "#F0F2F5", "#9BA3AF", "#4B5563", "#15171A"]
    width: 648
    height: settingType === "color" ? 86 : 42
    radius: 8
    color: "#23262C"
    border.width: 1
    border.color: "#343840"

    Text {
        x: 12; y: 12; width: 230; height: 18
        text: row.settingTitle
        color: "#C9CED6"
        fontSize: 11
        font.bold: true
    }

    Rectangle {
        x: 444; y: 7; width: 190; height: 28; radius: 7
        color: "#2B3038"; border.width: 1; border.color: "#424852"
        visible: row.settingType === "mode"
        Text { x: 9; y: 6; width: 20; height: 16; text: "‹"; color: "#8F98A5"; fontSize: 14 }
        Text {
            x: 30; y: 6; width: 130; height: 16
            text: String(row.currentValue)
            color: "#F0F2F5"; fontSize: 10; font.bold: true
            horizontalAlignment: Text.AlignHCenter
        }
        Text { x: 164; y: 6; width: 18; height: 16; text: "›"; color: "#8F98A5"; fontSize: 14 }
        MouseArea { x: 0; y: 0; width: 38; height: parent.height; onClicked: clickModel.cycleMode(row.moduleName, row.settingName, -1) }
        MouseArea { x: 152; y: 0; width: 38; height: parent.height; onClicked: clickModel.cycleMode(row.moduleName, row.settingName, 1) }
    }

    Rectangle {
        x: 594; y: 9; width: 40; height: 24; radius: 12
        color: Boolean(row.currentValue) ? "#E98BC1" : "#343840"
        border.width: 1
        border.color: Boolean(row.currentValue) ? "#F2A6D2" : "#484E58"
        visible: row.settingType === "boolean"
        Rectangle {
            x: Boolean(row.currentValue) ? 20 : 2; y: 2
            width: 18; height: 18; radius: 9
            color: Boolean(row.currentValue) ? "#FFFFFF" : "#7A828E"
        }
        MouseArea {
            anchors.fill: parent
            onClicked: clickModel.setBoolean(row.moduleName, row.settingName, !Boolean(row.currentValue))
        }
    }

    Item {
        x: 348; y: 5; width: 286; height: 32
        visible: row.settingType === "number"
        Text {
            x: 0; y: 1; width: 68; height: 14
            text: String(Math.round(Number(row.currentValue) * 100) / 100)
            color: "#9BA3AF"; fontSize: 10
            horizontalAlignment: Text.AlignRight
        }
        Rectangle {
            x: 80; y: 14; width: 206; height: 5; radius: 3; color: "#343840"
            Rectangle { width: Math.max(0, Math.min(parent.width, parent.width * row.ratio)); height: parent.height; radius: 3; color: "#E98BC1" }
            Rectangle { x: Math.max(0, Math.min(parent.width - 10, parent.width * row.ratio - 5)); y: -3; width: 11; height: 11; radius: 6; color: "#FFFFFF"; border.width: 1; border.color: "#E98BC1" }
            MouseArea {
                anchors.fill: parent
                onPressed: (mouse) => clickModel.setNumberByRatio(row.moduleName, row.settingName, mouse.x / width)
                onPositionChanged: (mouse) => {
                    if (pressed) clickModel.setNumberByRatio(row.moduleName, row.settingName, mouse.x / width)
                }
            }
        }
    }

    Text {
        x: 350; y: 12; width: 284; height: 18
        text: String(row.currentValue)
        color: "#9BA3AF"; fontSize: 10
        horizontalAlignment: Text.AlignRight
        visible: row.settingType === "text"
    }

    Rectangle {
        x: 570; y: 8; width: 64; height: 26; radius: 7
        color: String(row.currentValue)
        border.width: 1
        border.color: "#5C6573"
        visible: row.settingType === "color"
    }

    Item {
        x: 12; y: 45; width: 624; height: 30
        visible: row.settingType === "color"
        Repeater {
            model: row.swatches
            Rectangle {
                x: index * 51
                y: 0
                width: 42
                height: 26
                radius: 7
                color: modelData
                border.width: String(row.currentValue).toUpperCase() === String(modelData).toUpperCase() ? 2 : 1
                border.color: String(row.currentValue).toUpperCase() === String(modelData).toUpperCase() ? "#FFFFFF" : "#4B515C"
                MouseArea { anchors.fill: parent; onClicked: clickModel.setColorHex(row.moduleName, row.settingName, modelData) }
            }
        }
    }
}

import "."

Rectangle {
    id: card
    property bool entered: false
    property string moduleName: ""
    property string moduleTitle: ""
    property string moduleDescription: ""
    property string moduleIcon: "misc"
    property string keyName: "None"
    property bool hasSettings: false
    property bool active: false
    property bool expanded: clickModel.expandedModule === moduleName
    width: 680
    height: expanded ? 72 + settingColumn.height : 56
    radius: 12
    color: expanded ? "#262A31" : (active ? "#1C1B20" : (hover.containsMouse ? "#282C33" : "#23262C"))
    border.width: 1
    border.color: active ? "#553B4D" : (hover.containsMouse ? "#4A3A45" : "#343840")
    opacity: entered ? 1 : 0
    scale: entered ? 1 : 0.975
    Component.onCompleted: entered = true
    Behavior on opacity { NumberAnimation { duration: 170; easing.type: Easing.OutCubic } }
    Behavior on scale { NumberAnimation { duration: 210; easing.type: Easing.OutBack } }
    Behavior on height { NumberAnimation { duration: 220; easing.type: Easing.OutCubic } }
    Behavior on color { ColorAnimation { duration: 140 } }

    Rectangle {
        width: parent.width
        height: 56
        radius: 12
        color: card.active ? "#1C1B20" : (hover.containsMouse ? "#282C33" : "#23262C")
        visible: card.expanded
        Rectangle { y: 44; width: parent.width; height: 12; color: parent.color }
    }

    Rectangle { x: 0; y: 10; width: 3; height: 36; radius: 2; color: "#E98BC1"; visible: card.active }
    Rectangle {
        x: 16; y: 10; width: 36; height: 36; radius: 8
        color: card.active ? "#382833" : "#282C33"
        border.width: 1
        border.color: card.active ? "#65445B" : "#3A3F48"
    }
    VectorIcon {
        x: 25; y: 19; width: 18; height: 18
        iconName: card.moduleIcon
        tint: card.active ? "#E98BC1" : "#747D89"
        strokeWidth: 1.8
    }
    Text {
        x: 64; y: 9; width: 360; height: 20
        text: card.moduleTitle
        color: "#F0F2F5"
        fontSize: 13
        font.bold: true
    }
    Text {
        x: 64; y: 30; width: 430; height: 16
        text: card.moduleDescription
        color: "#68707D"
        fontSize: 11
    }
    Rectangle {
        x: 536; y: 13; width: 30; height: 30; radius: 8
        color: "#282C33"
        border.width: 1
        border.color: "#3D424C"
        visible: card.keyName !== "None"
        Text {
            anchors.fill: parent
            text: card.keyName
            color: "#8A929E"
            fontSize: 9
            font.bold: true
            horizontalAlignment: Text.AlignHCenter
            verticalAlignment: Text.AlignVCenter
        }
    }
    Rectangle {
        x: 574; y: 13; width: 30; height: 30; radius: 8
        color: settingsHover.containsMouse ? "#30353D" : "#282C33"
        border.width: 1
        border.color: card.active ? "#5C4054" : "#3D424C"
        visible: card.hasSettings
        VectorIcon {
            x: 7; y: 7; width: 16; height: 16
            iconName: "settings"
            tint: card.active ? "#E98BC1" : "#737B88"
            strokeWidth: 1.7
        }
        MouseArea {
            id: settingsHover
            anchors.fill: parent
            hoverEnabled: true
            onClicked: clickModel.toggleSettings(card.moduleName)
        }
    }
    Rectangle {
        x: 624; y: 16; width: 40; height: 24; radius: 12
        color: card.active ? "#E98BC1" : "#343840"
        border.width: 1
        border.color: card.active ? "#F2A6D2" : "#484E58"
        Rectangle {
            id: toggleKnob
            x: card.active ? 20 : 2
            y: 2
            width: 18
            height: 18
            radius: 9
            color: card.active ? "#FFFFFF" : "#7A828E"
            Behavior on x { NumberAnimation { duration: 180; easing.type: Easing.OutBack } }
            Behavior on color { ColorAnimation { duration: 140 } }
        }
        MouseArea {
            anchors.fill: parent
            hoverEnabled: true
            onClicked: clickModel.setModuleEnabled(card.moduleName, !card.active)
        }
    }
    MouseArea {
        id: hover
        x: 0; y: 0; width: 520; height: parent.height
        hoverEnabled: true
        acceptedButtons: 3
        onClicked: (mouse) => {
            if (mouse.button === 2 && card.hasSettings)
                clickModel.toggleSettings(card.moduleName)
        }
    }

    Rectangle {
        x: 16; y: 55; width: 648; height: 1
        color: "#3A3E46"
        visible: card.expanded
    }

    Column {
        id: settingColumn
        x: 16
        y: 64
        width: 648
        spacing: 6
        visible: card.expanded
        Repeater {
            model: card.expanded ? clickModel.settings : []
            SettingRow {
                moduleName: card.moduleName
                settingName: modelData.name
                settingTitle: modelData.displayName
                settingType: modelData.type
                currentValue: modelData.current
                ratio: modelData.ratio
            }
        }
    }
}

import "."

Rectangle {
    id: card
    property string categoryKey: ""
    property string categoryName: ""
    property string categoryIcon: "misc"
    property bool active: clickModel.activeCategory === categoryKey
    width: 200
    height: 40
    radius: 12
    color: active ? "#2A222B" : (hover.containsMouse ? "#282C33" : "#23262C")
    border.width: 1
    border.color: active ? "#68455E" : (hover.containsMouse ? "#414650" : "#343840")
    Behavior on color { ColorAnimation { duration: 140 } }
    Behavior on border.color { ColorAnimation { duration: 140 } }

    Rectangle { x: 0; y: 8; width: 3; height: 24; radius: 2; color: "#E98BC1"; visible: card.active }
    VectorIcon {
        x: 16; y: 11; width: 18; height: 18
        iconName: card.categoryIcon
        tint: card.active ? "#E98BC1" : (hover.containsMouse ? "#F0F2F5" : "#68707D")
        strokeWidth: 1.8
    }
    Text {
        x: 44; y: 11; width: 138; height: 18
        text: card.categoryName
        color: card.active ? "#F0F2F5" : (hover.containsMouse ? "#D7DBE1" : "#737B88")
        fontSize: 12.5
        font.bold: card.active
    }
    MouseArea {
        id: hover
        anchors.fill: parent
        hoverEnabled: true
        onClicked: clickModel.selectCategory(card.categoryKey)
    }
}

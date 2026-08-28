import "."

Rectangle {
    id: root
    property bool appeared: false
    width: 960
    height: 640
    color: "transparent"
    Component.onCompleted: appeared = true

    Rectangle {
        id: window
        x: 8
        y: 8
        width: 944
        height: 624
        radius: 20
        color: "#15171A"
        border.width: 1
        border.color: "#34363C"
        clip: true
        opacity: root.appeared ? 1 : 0
        scale: root.appeared ? 1 : 0.94
        Behavior on opacity { NumberAnimation { duration: 150; easing.type: Easing.OutCubic } }
        Behavior on scale { NumberAnimation { duration: 220; easing.type: Easing.OutBack } }

        Rectangle {
            id: titlebar
            width: parent.width
            height: 58
            radius: 20
            color: "#1B1D22"

            Rectangle { y: 20; width: parent.width; height: 38; color: "#1B1D22" }
            Rectangle { x: 92; y: 0; width: 760; height: 1; color: "#81506F" }
            Rectangle {
                x: 18; y: 13; width: 32; height: 32; radius: 9; color: "#E98BC1"
                VectorIcon { x: 7; y: 7; width: 18; height: 18; iconName: "brand"; tint: "#FFFFFF"; strokeWidth: 2.2 }
            }
            Text {
                x: 60; y: 18; width: 96; height: 24
                text: "Yozakura"; color: "#F0F2F5"; fontSize: 17; font.bold: true
            }
            Rectangle {
                x: 146; y: 20; width: 45; height: 19; radius: 5
                color: "#342632"; border.width: 1; border.color: "#63425A"
                Text { x: 7; y: 2; width: 34; height: 14; text: "v" + clickModel.version; color: "#E98BC1"; fontSize: 10; font.bold: true }
            }
            Rectangle {
                x: 467; y: 18; width: 28; height: 22; radius: 7
                color: "#22252B"; border.width: 1; border.color: "#4E5360"
                VectorIcon { x: 6; y: 3; width: 16; height: 16; iconName: "brand"; tint: "#E98BC1"; strokeWidth: 2.0 }
            }
            Rectangle {
                x: 812; y: 13; width: 34; height: 32; radius: 8
                color: languageArea.containsMouse ? "#262A31" : "transparent"
                Text {
                    anchors.fill: parent
                    text: clickModel.chinese ? "EN" : "中"
                    color: languageArea.containsMouse ? "#F0F2F5" : "#68707D"
                    fontSize: 11
                    horizontalAlignment: Text.AlignHCenter
                    verticalAlignment: Text.AlignVCenter
                }
                MouseArea { id: languageArea; anchors.fill: parent; hoverEnabled: true; onClicked: clickModel.toggleLanguage() }
            }
            Rectangle {
                x: 854; y: 13; width: 34; height: 32; radius: 8
                color: refreshArea.containsMouse ? "#262A31" : "transparent"
                VectorIcon { x: 8; y: 7; width: 18; height: 18; iconName: "refresh"; tint: refreshArea.containsMouse ? "#F0F2F5" : "#68707D" }
                MouseArea { id: refreshArea; anchors.fill: parent; hoverEnabled: true }
            }
            Rectangle {
                x: 896; y: 13; width: 34; height: 32; radius: 8
                color: closeArea.containsMouse ? "#3A242B" : "transparent"
                VectorIcon { x: 8; y: 7; width: 18; height: 18; iconName: "close"; tint: closeArea.containsMouse ? "#F87171" : "#68707D" }
                MouseArea { id: closeArea; anchors.fill: parent; hoverEnabled: true; onClicked: clickModel.requestClose() }
            }
            Rectangle { y: 57; width: parent.width; height: 1; color: "#303238" }
        }

        Rectangle {
            id: sidebar
            y: 58
            width: 220
            height: 566
            radius: 20
            color: "#1B1D22"

            Rectangle { width: parent.width; height: parent.height - 20; color: "#1B1D22" }
            Rectangle { x: 20; width: parent.width - 20; height: parent.height; color: "#1B1D22" }
            Text {
                x: 20; y: 18; width: 150; height: 16
                text: clickModel.chinese ? "分类" : "Categories"
                color: "#4E5561"; fontSize: 9; font.bold: true
            }
            Column {
                x: 10
                y: 38
                width: 200
                spacing: 6
                Repeater {
                    model: clickModel.categories
                    CategoryCard {
                        categoryKey: modelData.key
                        categoryName: modelData.displayName
                        categoryIcon: modelData.icon
                    }
                }
            }
            Rectangle {
                x: 10; y: 486; width: 200; height: 66; radius: 12
                color: "#23262C"; border.width: 1
                Rectangle { x: 12; y: 12; width: 34; height: 34; radius: 9; color: "#E98BC1" }
                Text { x: 23; y: 18; width: 16; height: 20; text: clickModel.username.length > 0 ? clickModel.username.substring(0, 1).toUpperCase() : "Y"; color: "#FFFFFF"; fontSize: 14; font.bold: true }
                Text { x: 57; y: 12; width: 126; height: 18; text: clickModel.username; color: "#F0F2F5"; fontSize: 12; font.bold: true }
                Rectangle { x: 57; y: 38; width: 5; height: 5; radius: 3; color: "#34D399" }
                Text { x: 67; y: 33; width: 70; height: 16; text: "PREMIUM"; color: "#34D399"; fontSize: 9; font.bold: true }
            }
            Rectangle { x: 219; width: 1; height: parent.height; color: "#303238" }
        }

        Rectangle {
            id: content
            x: 220
            y: 58
            width: 724
            height: 566
            color: "transparent"

            Text {
                x: 22; y: 22; width: 260; height: 30
                text: clickModel.title; color: "#F0F2F5"; fontSize: 20; font.bold: true
            }
            Rectangle {
                x: 472; y: 16; width: 230; height: 36; radius: 8
                color: "#23262C"; border.width: 1; border.color: "#343840"
                border.color: searchInput.activeFocus ? "#A8658C" : "#343840"
                VectorIcon { x: 11; y: 10; width: 15; height: 15; iconName: "search"; tint: "#59616E"; strokeWidth: 1.8 }
                Text {
                    x: 34; y: 10; width: 170; height: 18
                    text: clickModel.chinese ? "搜索模块..." : "Search modules..."
                    color: "#59616E"; fontSize: 12
                    visible: searchInput.text.length === 0
                }
                TextInput {
                    id: searchInput
                    x: 34; y: 8; width: 168; height: 22
                    text: clickModel.search
                    color: "#F0F2F5"
                    fontSize: 12
                    verticalAlignment: TextInput.AlignVCenter
                    selectionColor: "#E98BC1"
                    selectedTextColor: "#FFFFFF"
                    clip: true
                    onTextChanged: clickModel.setSearch(text)
                }
                Rectangle {
                    x: 204; y: 9; width: 18; height: 18; radius: 9
                    color: clearSearch.containsMouse ? "#343840" : "#2A2E35"
                    visible: searchInput.text.length > 0
                    VectorIcon { x: 4; y: 4; width: 10; height: 10; iconName: "close"; tint: "#8A929E"; strokeWidth: 1.8 }
                    MouseArea {
                        id: clearSearch
                        anchors.fill: parent
                        hoverEnabled: true
                        onClicked: { searchInput.text = ""; searchInput.forceActiveFocus() }
                    }
                }
            }
            Flickable {
                id: moduleScroll
                x: 22
                y: 70
                width: 680
                height: 476
                contentWidth: width
                contentHeight: moduleColumn.height
                clip: true
                Column {
                    id: moduleColumn
                    width: 680
                    spacing: 8
                    Repeater {
                        model: clickModel.modules
                        ModuleCard {
                            moduleName: modelData.name
                            moduleTitle: modelData.displayName
                            moduleDescription: modelData.description
                            moduleIcon: modelData.icon
                            keyName: modelData.keyName
                            hasSettings: modelData.hasSettings
                            active: modelData.enabled
                        }
                    }
                }
            }
        }
    }
}

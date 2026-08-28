Item {
    id: root
    property string iconName: ""
    property string tint: "#9BA3AF"
    property real strokeWidth: 1.8

    function line(ctx, x1, y1, x2, y2) {
        ctx.beginPath()
        ctx.moveTo(x1, y1)
        ctx.lineTo(x2, y2)
        ctx.stroke()
    }

    function circle(ctx, x, y, radius) {
        ctx.beginPath()
        ctx.arc(x, y, radius, 0, Math.PI * 2)
        ctx.stroke()
    }

    function polyline(ctx, points, close) {
        ctx.beginPath()
        ctx.moveTo(points[0], points[1])
        for (var i = 2; i < points.length; i = i + 2) {
            ctx.lineTo(points[i], points[i + 1])
        }
        if (close) ctx.closePath()
        ctx.stroke()
    }

    function draw(ctx) {
        ctx.clearRect(0, 0, width, height)
        ctx.save()
        ctx.scale(width / 24, height / 24)
        ctx.strokeStyle = tint
        ctx.lineWidth = strokeWidth
        ctx.lineCap = "round"
        ctx.lineJoin = "round"

        if (iconName === "brand") {
            polyline(ctx, [12,2, 2,7, 12,12, 22,7, 12,2], false)
            polyline(ctx, [2,12, 12,17, 22,12], false)
            polyline(ctx, [2,17, 12,22, 22,17], false)
        } else if (iconName === "combat") {
            polyline(ctx, [14.5,17.5, 3,6, 3,3, 6,3, 17.5,14.5], false)
            line(ctx, 13,19, 19,13)
            line(ctx, 16,16, 20,20)
            line(ctx, 19,21, 21,19)
        } else if (iconName === "movement") {
            line(ctx, 13,4,13,20)
            polyline(ctx, [17,8, 13,4, 9,8], false)
            line(ctx, 4,12,11,12)
        } else if (iconName === "render") {
            ctx.beginPath()
            ctx.moveTo(2,12)
            ctx.arcTo(6,5,12,5,7)
            ctx.arcTo(18,5,22,12,7)
            ctx.arcTo(18,19,12,19,7)
            ctx.arcTo(6,19,2,12,7)
            ctx.stroke()
            circle(ctx, 12,12,3)
        } else if (iconName === "player") {
            circle(ctx, 12,7,4)
            ctx.beginPath()
            ctx.moveTo(4,21)
            ctx.arcTo(4,15,8,15,4)
            ctx.lineTo(16,15)
            ctx.arcTo(20,15,20,21,4)
            ctx.stroke()
        } else if (iconName === "world") {
            circle(ctx, 12,12,10)
            line(ctx, 2,12,22,12)
            ctx.beginPath()
            ctx.moveTo(12,2)
            ctx.arcTo(17,7,16,12,8)
            ctx.arcTo(17,17,12,22,8)
            ctx.arcTo(7,17,8,12,8)
            ctx.arcTo(7,7,12,2,8)
            ctx.stroke()
        } else if (iconName === "misc" || iconName === "settings") {
            circle(ctx, 12,12,3)
            circle(ctx, 12,12,8)
            for (var g = 0; g < 8; g = g + 1) {
                var angle = g * Math.PI / 4
                line(ctx, 12 + Math.cos(angle) * 8, 12 + Math.sin(angle) * 8,
                    12 + Math.cos(angle) * 10.5, 12 + Math.sin(angle) * 10.5)
            }
        } else if (iconName === "aim") {
            circle(ctx, 12,12,9)
            circle(ctx, 12,12,5)
            circle(ctx, 12,12,1.5)
        } else if (iconName === "bot") {
            circle(ctx, 12,13,5)
            line(ctx, 12,3,12,8)
            line(ctx, 8,5,10,8)
            line(ctx, 16,5,14,8)
            circle(ctx, 10,13,0.7)
            circle(ctx, 14,13,0.7)
        } else if (iconName === "click") {
            polyline(ctx, [15,15, 13,20, 9,9, 20,13, 15,15, 20,20], false)
        } else if (iconName === "plus") {
            circle(ctx, 12,12,10)
            line(ctx, 12,8,12,16)
            line(ctx, 8,12,16,12)
        } else if (iconName === "bolt") {
            polyline(ctx, [13,2, 3,14, 12,14, 11,22, 21,10, 12,10, 13,2], true)
        } else if (iconName === "reach") {
            polyline(ctx, [15,3, 21,3, 21,9], false)
            line(ctx, 21,3,14,10)
        } else if (iconName === "sun") {
            circle(ctx, 12,12,5)
            line(ctx, 12,1,12,3)
            line(ctx, 12,21,12,23)
            line(ctx, 1,12,3,12)
            line(ctx, 21,12,23,12)
            line(ctx, 4.2,4.2,5.6,5.6)
            line(ctx, 18.4,18.4,19.8,19.8)
            line(ctx, 4.2,19.8,5.6,18.4)
            line(ctx, 18.4,5.6,19.8,4.2)
        } else if (iconName === "shield") {
            polyline(ctx, [12,2, 20,6, 20,12, 19,16, 16,20, 12,23, 8,20, 5,16, 4,12, 4,6, 12,2], true)
        } else if (iconName === "chest") {
            ctx.strokeRect(2,7,20,14)
            ctx.beginPath()
            ctx.moveTo(8,7)
            ctx.arcTo(8,3,12,3,4)
            ctx.arcTo(16,3,16,7,4)
            ctx.stroke()
            line(ctx, 12,11,12,17)
            line(ctx, 9,14,15,14)
        } else if (iconName === "tag") {
            polyline(ctx, [4,3, 20,3, 22,5, 22,13, 20,15, 8,15, 4,19, 4,3], false)
            line(ctx, 8,8,16,8)
            line(ctx, 8,12,13,12)
        } else if (iconName === "clock") {
            circle(ctx, 12,12,9)
            line(ctx, 12,7,12,12)
            line(ctx, 12,12,16,14)
        } else if (iconName === "ban") {
            circle(ctx, 12,12,10)
            line(ctx, 5,5,19,19)
        } else if (iconName === "box") {
            ctx.strokeRect(3,3,18,18)
            line(ctx, 9,3,9,21)
        } else if (iconName === "search") {
            circle(ctx, 10.5,10.5,7)
            line(ctx, 16,16,21,21)
        } else if (iconName === "palette") {
            circle(ctx, 12,12,9)
            ctx.beginPath()
            ctx.moveTo(12,3)
            ctx.arcTo(21,6,21,12,9)
            ctx.arcTo(21,20,12,21,8)
            ctx.arcTo(5,21,5,14,7)
            ctx.arcTo(5,9,12,9,4)
            ctx.arcTo(16,9,16,5,4)
            ctx.arcTo(16,3,12,3,4)
            ctx.stroke()
        } else if (iconName === "close") {
            line(ctx, 6,6,18,18)
            line(ctx, 18,6,6,18)
        } else if (iconName === "refresh") {
            ctx.beginPath()
            ctx.arc(12,12,8,0.35,Math.PI * 1.75)
            ctx.stroke()
            polyline(ctx, [8,3, 12,4, 10,8], false)
        }
        ctx.restore()
    }

    onIconNameChanged: iconCanvas.requestPaint()
    onTintChanged: iconCanvas.requestPaint()
    onStrokeWidthChanged: iconCanvas.requestPaint()

    Canvas {
        id: iconCanvas
        anchors.fill: parent
        onPaint: root.draw(getContext("2d"))
    }
}

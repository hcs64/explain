function supports_canvas() {
    return !!document.createElement('canvas').getContext;
}

function supports_canvas_text() {
    if (!supports_canvas()) { return false; }
    var dummy_canvas = document.createElement('canvas');
    var context = dummy_canvas.getContext('2d');
    return typeof context.fillText == 'function';
}

function supports_html5_storage() {
    try {
        return 'localStorage' in window && window['localStorage'] !== null;
    } catch (e) {
        return false;
    }
}

function getCursorPosition(el, ev) {
    // on the recommendation of HTML5: Up and Running
    var x, y;
    if (ev.pageX != undefined && ev.pageY != undefined) {
        x = ev.pageX;
        y = ev.pageY;
    } else {
        x = ev.clientX + document.body.scrollLeft + document.documentElement.scrollLeft;
        y = ev.clientY + document.body.scrollTop + document.documentElement.scrollTop;
    }

    x -= el.offsetLeft;
    y -= el.offsetTop;

    return {x: x, y: y};
}

function drawDot(context, p) {
    context.fillRect(p.x, p.y, 3, 3);
}

var clearCanvas;

function drawCurrentArrow(arrow, context) {
    clearCanvas();
    context.beginPath();
    context.moveTo(arrow.start.x+0.5, arrow.start.y+0.5);
    context.lineTo(arrow.end.x+0.5, arrow.end.y+0.5);
    context.stroke();
}

function initCanvas() {
    var arrow = {};

    if (!supports_canvas()) {
        return;
    }

    var canvasElement = document.createElement("canvas");
    canvasElement.id = "test_canvas";
    document.body.appendChild(canvasElement);
    canvasElement.width = 640;
    canvasElement.height= 480;

    clearCanvas = function () {
        canvasElement.height = 480;
    }

    context = canvasElement.getContext("2d");

    initMouseEvents(canvasElement, {
        drag: function (p) {
            arrow.end = p;
            drawCurrentArrow(arrow, context);
        },
        pickup: function (p) {
            arrow = {start: p, end: p};
            drawCurrentArrow(arrow, context);
        },
        drop: function (p) {
            arrow.end = p;
            //commitCurrentArrow(arrow, context);
        }
    });

}

function initMouseEvents(canvasElement, fcnDict) {
    var in_range = false;
    var dragging = false;
    var last_point = {x:0, y:0};

    var handleMouseDrags = function (e, old_dragging) {
        var p = getCursorPosition(canvasElement, e);

        if (!old_dragging && dragging) {
            fcnDict.pickup(p);
            last_point = p;
        }
        if (old_dragging && !dragging) {
            fcnDict.drop(last_point);
        }
        if (old_dragging && dragging) {
            fcnDict.drag(p);
            last_point = p;
        }
    };

    canvasElement.addEventListener("mousedown", function (e) {
        var old_dragging = dragging;

        if (in_range && e.buttons == 1) {
            dragging = true;
        }

        handleMouseDrags(e, old_dragging);
    }, false);
    canvasElement.addEventListener("mouseup", function (e) {
        var old_dragging = dragging;

        if (in_range && e.buttons == 1) {
            dragging = false;
        }

        handleMouseDrags(e, old_dragging);
    }, false);
    canvasElement.addEventListener("mousemove", function (e) {
        if (in_range && dragging) {
            handleMouseDrags(e, dragging);
        }
    }, false);
    canvasElement.addEventListener("mouseover", function (e) {
        in_range = true;
    }, false);
    canvasElement.addEventListener("mouseout", function (e) {
        var old_dragging = dragging;

        in_range = false;
        dragging = false;
        handleMouseDrags(e, old_dragging);
    }, false);
}

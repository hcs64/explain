// canvas dimensions
var kWidth = 640;
var kHeight= 480;

// http://paulirish.com/2011/requestanimationframe-for-smart-animating/
// http://my.opera.com/emoller/blog/2011/12/20/requestanimationframe-for-smart-er-animating

// requestAnimationFrame polyfill by Erik Möller
// fixes from Paul Irish and Tino Zijdel
(function() {
    var lastTime = 0;
    var vendors = ['ms', 'moz', 'webkit', 'o'];
    for(var x = 0; x < vendors.length && !window.requestAnimationFrame; ++x) {
        window.requestAnimationFrame = window[vendors[x]+'RequestAnimationFrame'];
        window.cancelAnimationFrame = 
          window[vendors[x]+'CancelAnimationFrame'] || window[vendors[x]+'CancelRequestAnimationFrame'];
    }
 
    if (!window.requestAnimationFrame)
        window.requestAnimationFrame = function(callback, element) {
            var currTime = new Date().getTime();
            var timeToCall = Math.max(0, 16 - (currTime - lastTime));
            var id = window.setTimeout(function() { callback(currTime + timeToCall); }, 
              timeToCall);
            lastTime = currTime + timeToCall;
            return id;
        };
 
    if (!window.cancelAnimationFrame)
        window.cancelAnimationFrame = function(id) {
            clearTimeout(id);
        };
}());

function supports_html5_storage() {
    try {
        return 'localStorage' in window && window['localStorage'] !== null;
    } catch (e) {
        return false;
    }
}

// on the recommendation of HTML5: Up and Running
function supports_canvas() {
    return !!document.createElement('canvas').getContext;
}

function supports_canvas_text() {
    if (!supports_canvas()) { return false; }
    var dummy_canvas = document.createElement('canvas');
    var context = dummy_canvas.getContext('2d');
    return typeof context.fillText == 'function';
}

function getCursorPosition(el, ev) {
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

function initMouseEvents(canvasElement, clickFcn, pickupFcn, dragFcn, dropFcn) {
    var in_range = false;
    var dragging = false;
    var last_point = {x:0, y:0};

    var handleMouseDrags = function (e, old_dragging) {
        var p = getCursorPosition(canvasElement, e);

        if (!old_dragging && dragging) {
            pickupFcn(p);
            last_point = p;
        }
        if (old_dragging && !dragging) {
            dropFcn(last_point);
        }
        if (old_dragging && dragging) {
            dragFcn(p);
            last_point = p;
        }
    };

    canvasElement.addEventListener("mousedown", function (e) {
        var old_dragging = dragging;

        if (in_range) {
            if (!clickFcn(getCursorPosition(canvasElement, e))) {
                // click didn't capture this mousedown
                dragging = true;
            }
        }

        handleMouseDrags(e, old_dragging);
    }, false);
    canvasElement.addEventListener("mouseup", function (e) {
        var old_dragging = dragging;

        if (in_range) {
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

function addSimRenderer(backend, renderCanvasId, width, height) {
    var canvasElement;
    var renderContext;
    var animationStartTime;
    var lastFrameTime;
    var animateFcn;

    if (!supports_canvas_text()) {
        return;
    }

    canvasElement = document.createElement("canvas");
    canvasElement.id = renderCanvasId;
    document.body.appendChild(canvasElement);
    canvasElement.width = width
    canvasElement.height= height;
    renderContext = canvasElement.getContext("2d");

    animationStartTime = Date.now();
    lastFrameTime = animationStartTime;
    animateFcn = function (time) {
        var dt = time-lastFrameTime;

        var handle = window.requestAnimationFrame(animateFcn);

        if (!backend.render(canvasElement, renderContext, time-animationStartTime, dt)) {
            window.cancelAnimationFrame(handle);
        }

        lastFrameTime = time;
    }

    initMouseEvents(canvasElement,
        function (p) {
            if (typeof backend.mouseClick == 'function') {
                return backend.mouseClick(p);
            } else {
                return false;
            }
        },
        function (p) {
            if (typeof backend.mousePickup == 'function') {
                return backend.mousePickup(p);
            } else {
                return false;
            }
        },
        function (p) {
            if (typeof backend.mouseDrag == 'function') {
                return backend.mouseDrag(p);
            } else {
                return false;
            }
        },
        function (p) {
            if (typeof backend.mouseDrop == 'function') {
                return backend.mouseDrop(p);
            } else {
                return false;
            }
        }
    );

    window.requestAnimationFrame(animateFcn);
}

function AABB(_minx, _miny, _width, _height) {
    this.minx = _minx;
    this.miny = _miny;
    this.maxx = _minx + _width;
    this.maxy = _miny + _height
    this.width = _width;
    this.height = _height;

    this.contains = function(p) {
        return (p.x >= this.minx && p.y >= this.miny &&
                p.x <  this.maxx && p.y <  this.maxy);
    };
    this.move = function(x,y) {
        this.minx += x;
        this.miny += y;
        this.maxx += x;
        this.maxy += y;
    };
}

function constructButton(args) {
    var text, callback, aabb;

    var that;
    
    that = {};

    text        = args.text;
    aabb        = args.aabb;
    callback    = args.callback;

    that.contains = function(p) {
        return aabb.contains(p);
    };

    that.click = function(p) {
        callback(p);
    };

    that.render = function(context) {
        context.fillStyle = "#000";
        context.fillRect(aabb.minx, aabb.miny, aabb.width, aabb.height);

        context.font = "bold 12px sans-serif";
        context.fillStyle = "white";
        context.textBaseline = "middle";
        context.textAlign = "center";
        context.fillText(text, aabb.minx+aabb.width/2, aabb.miny+aabb.height/2);
    };

    return that;
}

function constructIOArray() {
    var i;
    var a = [];
    a.names = [];
    a.indexes = {};

    for (i = 0; i < arguments.length/2; i++) {
        a.names[i] = arguments[i*2];
        a.indexes[arguments[i*2]] = i;
        a[i] = arguments[i*2+1];
    }

    return a;
}

function constructNode(title) {
    var that;
    var aabb, titleaabb;
    var last_updated, update_in_progress;

    aabb = new AABB(10,10,100,200);
    titleaabb = new AABB(10,10,100,20);

    that = {};
    that.inputs = [];
    that.outputs = [];
    
    that.render = function (context, t, dt) {
        context.fillStyle = "black";
        context.fillRect(aabb.minx, aabb.miny, aabb.width, aabb.height);

        context.fillStyle = "white";
        context.fillRect(titleaabb.minx+2, titleaabb.miny+2, titleaabb.width-4, titleaabb.height-4);

        context.fillStyle = "black";
        context.font = "bold 12px sans-serif";
        context.textBaseline = "middle";
        context.textAlign = "center";
        context.fillText(title, titleaabb.minx+titleaabb.width/2, titleaabb.miny+titleaabb.height/2);

        drawIO(context, that.inputs, aabb.minx+2, 40);
        drawIO(context, that.outputs, aabb.maxx-2-40, 40);
    };

    var drawIO = function(context, list, x, width) {
        var i, name;
        for (i = 0; i < list.length; i++) {
            name = list.names[i];
            y = 30 + i*22;

            context.fillStyle = "white";
            context.fillRect(x, aabb.miny+2+y, width, 20);

            context.fillStyle = "black";
            context.font = "bold 12px sans-serif";
            context.textBaseline = "top";
            context.textAlign = "left";
            context.fillText(name, x, aabb.miny+2+y);
        }
    };

    that.contains = function(p) {
        return aabb.contains(p);
    };

    that.checkHitTitle = function(p) {
        return titleaabb.contains(p);
    };

    var checkHitIO = function(list, x, width, p) {
        var i, iobb;

        for (i = 0; i < list.length; i++) {
            iobb = new AABB(x, aabb.miny+2+30+i*22, width, 20);

            if (iobb.contains(p)) {
                return i;
            }
        }
        return null;
    };

    that.checkHitInput = function(p) {
        return checkHitIO(this.inputs, aabb.minx+2, 40, p);
    };
    
    that.checkHitOutput = function(p) {
        return checkHitIO(this.outputs, aabb.maxx-2-40, 40, p);
    };

    that.pickup = function(p) {
        lastpos = p;
    };

    that.drag = function(p) {
        aabb.move(p.x-lastpos.x, p.y-lastpos.y);
        titleaabb.move(p.x-lastpos.x, p.y-lastpos.y);
        lastpos = p;
    };

    that.drop = function(p) {
        // nothin'
    };

    // dataflow handling
    that.dependencies = [];
    last_updated = -1;
    update_in_progress = false;

    that.addDependency = function(other_node) {
        that.dependencies.push(other_node);
    };

    /*
    clients = [];
    that.addClient = function(other_node) {
        that.clients.push(other_node);
    }
    */

    that.isOutOfDate = function(new_stamp) {
        if (last_updated < new_stamp) {
            return true;
        } else {
            return false;
        }
    };

    that.markUpdatePending = function() {
        update_in_progress = true;
    };

    that.isUpdatePending = function() {
        return update_in_progress;
    };

    that.markUpdated = function(new_stamp) {
        last_updated = new_stamp;
        update_in_progress = false;
    };

    return that;
}

function constructWaveNode() {
    var that;
    var super_render;

    that = constructNode("wave!");

    super_render = that.render;

    that.render = function (context, t, dt) {
        super_render(context, dt, dt);
        context.fillStyle = "white";
        context.font = "bold 12px sans-serif";
        context.textBaseline = "middle";
        context.textAlign = "center";
    };

    var gset = function(name, value) {that.outputs[that.outputs.indexes[name]] = value;};
    var g = function(name) {
        var v = that.inputs[that.inputs.indexes[name]];
        if (typeof v == 'function') {
            return v();
        }
        return v;
    };

    that.inputs = constructIOArray("period", 1000, "amplitude", 50, "offset", 0);
    that.outputs = constructIOArray("osc", 0);
    that.update = function (context, t, dt) {
        gset("osc", Math.sin((t+g('offset'))/g('period')*Math.PI*2)*g('amplitude'));
    };

    return that;
}

function constructImageNode() {
    var that;
    var super_render;

    that = constructNode("image!");
    
    super_render = that.render;

    that.render = function (context, t, dt) {
        super_render (context, t, dt);

        // imagine this is an image
    };

    var g = function(name) {
        var v = that.inputs[that.inputs.indexes[name]];
        if (typeof v == 'function') {
            return v();
        }
        return v;
    };

    that.update = function (context, t, dt) {
        context.strokeStyle = g('color');
        context.beginPath();
        context.arc(g('x')+128, g('y')+128, g('scale')*30, 0, Math.PI*2, false);
        context.closePath();
        context.stroke();
    };

    that.inputs = constructIOArray("x", 0, "y", 0, "scale", 1.0, "color", "red");

    return that;
}

// the pre-built backend, main execution environment
function constructBasicBackend(prompt_id) {
var that;
var buttons;
var nodes;
var pipes;
var dragging;

var active_pipe;

that = {};

dragging = null;
active_pipe = {};

buttons = [];
nodes = [];
pipes = [];

buttons = [
    constructButton({
        text:   "wave",
        aabb:   new AABB(0,100,50,50),
        callback: function() {
            nodes.push(constructWaveNode());
        }
    }),

    constructButton({
        text:   "image",
        aabb:   new AABB(0,170,50,50),
        callback: function() {
            nodes.push(constructImageNode());
        }
    }),
];

nodes = [];
pipes = [];


/*
// init arrows (load from localStorage if possible)
arrows = (function () {
    var arrows, arrowcount;

    if (!supports_html5_storage()) {
        return [];
    }

    arrowcount = parseInt(localStorage["test.arrowcount"]);
    if (isNaN(arrowcount)) {
        arrowcount = 0;
    }


    var parsePoints = function(s) {
        var a;
        if (typeof s != 'string') {
            return [];
        }
        
        a = s.split(',');
        if (a.length != 4) {
            return [];
        }

        return {start: {x: parseInt(a[0]),
                        y: parseInt(a[1])},
                  end: {x: parseInt(a[2]),
                        y: parseInt(a[3])}};
    };

    for (i = 0; i < arrowcount; i++) {
        arrows[i] = parsePoints(localStorage["test.arrow"+i]);
        window.console.log(localStorage["test.arrow"+i]);
        if (!('start' in arrows[i])) {
            return [];
        }
    }

    return arrows;
})();

var saveArrows = function () {
    var i;

    if (!supports_html5_storage()) {
        return;
    }

    localStorage["test.arrowcount"] = arrows.length;

    for (i = 0; i < arrows.length; i++) {
        localStorage["test.arrow"+i] = arrows[i].start.x +"," + arrows[i].start.y + "," + arrows[i].end.x + "," + arrows[i].end.y;
    }
};

var resetArrows = function () {
    arrows = [];
    localStorage.clear();
};
*/

var connectNodes = function(source_node, source_idx, dest_node, dest_idx) {
    dest_node.addDependency(source_node);
    dest_node.inputs[dest_idx] = function () {
        return source_node.outputs[source_idx];
    };
}

var constructPipe = function (start_node, output_idx, start_point) {
    var that;
    var current_point;
    var end_node;
    var input_idx;
    
    that = {};

    current_point = start_point;

    that.drag = function (p) {
        current_point = p;

        // TODO: would be nice to preview, but we'll need detach
    }

    that.drop = function (p) {
        var i;
        end_node = null;

        current_point = p;
        for (i = 0; i < nodes.length; i++) {
            if (nodes[i].contains(p)) {
                input_idx = nodes[i].checkHitInput(p);
                if (input_idx !== null) {
                    end_node = nodes[i];
                    break;
                }
            }
        }

        if (!!end_node) {
            pipes.push(that);

            connectNodes(start_node, output_idx, end_node, input_idx);
        }
    }

    that.render = function (context) {
        var drawArrow = function (arrow) {
            var sx, sy;
            var ex, ey;
            var dx, dy;
            var mag;
            var head_ang = Math.PI/4;

            sx = arrow.start.x;
            sy = arrow.start.y;
            ex = arrow.end.x;
            ey = arrow.end.y;
            dx = ex-sx;
            dy = ey-sy;
            mag = Math.sqrt(dx*dx+dy*dy);

            if (mag == 0) {
                dx = 0;
                dy = 0;
            }
            else {
                dx = -dx / mag * 10;
                dy = -dy / mag * 10;
            }
            
            context.strokeStyle = "blue";
            context.beginPath();
            context.moveTo(sx+0.5, sy+0.5);
            context.lineTo(ex+0.5, ey+0.5);

            context.moveTo(ex+0.5-dy*Math.sin(head_ang)+dx*Math.cos(head_ang),
                           ey+0.5+dx*Math.sin(head_ang)+dy*Math.cos(head_ang));
            context.lineTo(ex+0.5, ey+0.5);
            context.lineTo(ex+0.5-dy*Math.sin(-head_ang)+dx*Math.cos(-head_ang),
                           ey+0.5+dx*Math.sin(-head_ang)+dy*Math.cos(-head_ang));
            context.stroke();
        };

        drawArrow({start: start_point, end: current_point});
    };

    return that;
}

var promptForNumberInput = function(node, idx) {
    var prompt_element;
    var listener;
    
    prompt_element = document.getElementById(prompt_id);

    prompt_element.value = node.inputs[idx];

    listener = function () {
        node.inputs[idx] = parseFloat(prompt_element.value);
        prompt_element.form.removeEventListener("submit", listener, true);
        return false;
    };
    prompt_element.form.addEventListener("submit", listener, true);

    prompt_element.focus();
}

// public methods
that.mouseClick = function (p) {
    var i, endpoint;

    // nodes are in front of buttons
    for (i = nodes.length-1; i >= 0; i--) {
        if (nodes[i].contains(p)) {

            endpoint = nodes[i].checkHitInput(p);
            if (endpoint !== null) {
                // clicked on an input, we may be able to set it

                if (typeof nodes[i].inputs[endpoint] == 'number') {
                    promptForNumberInput(nodes[i], endpoint);
                    return false;
                }
            }
            // node still captures click
            return false;
        }
    }

    for (i = buttons.length-1; i >= 0; i--) {
        if (buttons[i].contains(p)) {
            buttons[i].click(p);
            return true;
        }
    }

    return false;
};

that.mousePickup = function (p) {
    var i, endpoint;

    if (!!dragging) {
        window.console.log("had to drop something already being dragged on pickup");
        dragging.drop(p);
        dragging = null;
    }

    // drag nodes around
    for (i = nodes.length-1; i >= 0; i--) {
        if (nodes[i].contains(p)) {
            if (nodes[i].checkHitTitle(p)) {
                // drag a node by its titlebar
                nodes[i].pickup(p);

                dragging = nodes[i];
                return true;
            }
            else {
                // drag from outputs to make pipes
                endpoint = nodes[i].checkHitOutput(p);
                if (endpoint !== null) {
                    dragging = constructPipe(nodes[i], endpoint, p);
                    return true;
                }
            }
        }
    }
    return true;
};

that.mouseDrag = function (p) {
    if (!!dragging) {
        dragging.drag(p);
    }
    return true;
};

that.mouseDrop = function (p) {
    if (!!dragging) {
        dragging.drop(p);
    }
    dragging = null;
    return true;
};

that.render = function(canvasElement, context, t, dt) {
    var w = canvasElement.width;
    var h = canvasElement.height;
    var i;

    context.clearRect(0, 0, w, h);

    /*
    for (i = 0; i < arrows.length; i++) {
        drawArrow(arrows[i]);

        arrows[i].start.y += dt/1000*100;
        arrows[i].end.y += dt/1000*100;

        if (arrows[i].start.y > h && arrows[i].end.y > h) {
            var distdiff = Math.abs(arrows[i].end.y - arrows[i].start.y); 
            arrows[i].start.y -= kHeight + distdiff;
            arrows[i].end.y -= kHeight +   distdiff;
        }
    }

    // draw the currently-dragged arrow
    if ('start' in arrow) {
        // arrowhead
        drawArrow(arrow);
    }
    */

    // render buttons
    for (i = 0; i < buttons.length; i++) {
        buttons[i].render(context);
    }

    // render nodes
    for (i = 0; i < nodes.length; i++) {
        nodes[i].render(context, t, dt);
    }

    // render pipes
    for (i = 0; i < pipes.length; i++) {
        pipes[i].render(context);
    }

    // highest priority: current dragster (could be redundant)
    if (!!dragging && 'render' in dragging && typeof dragging.render == 'function') {
        dragging.render(context, t, dt);
    }


    // keep runnning me
    return true;
};

that.getInterpreter = function () {
    var interpreterBackend;

    interpreterBackend = {};

    interpreterBackend.render = function (canvasElement, context, t, dt) {
        var node_queue;
        var i, n, j, d, exploring_dependencies;
        context.clearRect(0, 0, 256, 256);

        // TODO: should just compute update order once every graph edit,
        // that's kind of the point of this whole project

        // first queue up those with no "output", these must be displaying something
        node_queue = []
        for (i = 0; i < nodes.length; i++) {
            if (nodes[i].outputs.length == 0) {
                nodes[i].markUpdatePending();
                node_queue.push(nodes[i]);
            }
        }

        while (node_queue.length > 0) {
            exploring_dependencies = false;
            n = node_queue.shift();
            if (n.isOutOfDate(t)) {
                for (j = 0; j < n.dependencies.length; j++) {
                    d = n.dependencies[j];
                    if (d.isOutOfDate(t) && !d.isUpdatePending()) {
                        exploring_dependencies = true;
                        d.markUpdatePending();
                        node_queue.push(d);
                    }
                }

                if (exploring_dependencies) {
                    node_queue.push(n);
                } else {
                    n.update(context, t, dt);
                    n.markUpdated(t);
                }
            }
        }
        return true;
    };

    return interpreterBackend;
};

return that;

}; // end of BasicBackend

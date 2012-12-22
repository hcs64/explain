var mouseCallbackNames = ['click','pickup','drag','drop'];

function initMouseEvents(canvasElement, callbacks) {
    var dummy       = function() {return false;}
    var clickFcn    = callbacks.click   || dummy;
    var pickupFcn   = callbacks.pickup  || dummy;
    var dragFcn     = callbacks.drag    || dummy;
    var dropFcn     = callbacks.drop    || dummy;

    // is the cursor over this canvas?
    var in_range = false;
    // are we currently dragging?
    var dragging = false;
    // where we're dragged to so far
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
            // first try to treat it as a click
            if (!clickFcn(getCursorPosition(canvasElement, e))) {
                // click didn't capture this mousedown, begin drag
                dragging = true;
            }
        }

        handleMouseDrags(e, old_dragging);
    }, false);

    canvasElement.addEventListener("mouseup", function (e) {
        var old_dragging = dragging;

        // I'm not sure of the wisdom of this check, seems like we should
        // always stop dragging here.
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

//
function addSimRenderer(backend, renderCanvasId, width, height) {
    var canvasElement;
    var renderContext;
    var animationStartTime;
    var lastFrameTime;
    var animateFcn;
    var mouseCallbacks;
    var i, cb;

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

    mouseCallbacks = {}
    for (i = 0; i < mouseCallbackNames.length; i++) {
        cb = backend['mouse'+mouseCallbackNames[i]];
        if (typeof cb == 'function') {
            mouseCallbacks[mouseCallbackNames[i]] = cb;
        }
    }

    initMouseEvents(canvasElement, mouseCallbacks);

    window.requestAnimationFrame(animateFcn);
}

// bounding boxes, only to be used in pixel-space
var boundingBoxProto = {
    contains: function(p) {
        return (p.x >= this.minx && p.y >= this.miny &&
                p.x <= this.maxx && p.y <= this.maxy);
    },

    moveTo: function(x,y) {
        this.minx = x;
        this.miny = y;
        this.maxx = x+this.width;
        this.maxy = y+this.height;
    },

    moveBy: function(x,y) {
        this.moveTo(this.minx+x, this.miny+y);
    },

    getMinX: function() { return this.minx; },
    getMinY: function() { return this.miny; },
    getMaxX: function() { return this.maxx; },
    getMaxY: function() { return this.maxy; },
    getMidX: function() { return this.minx+this.width/2; },
    getMidY: function() { return this.miny+this.height/2; },
};

function constructBB(minx, miny, width, height) {
    var F = function(){};
    F.prototype = boundingBoxProto;
    obj = new F;

    obj.width = width;
    obj.height = height;

    obj.moveTo(minx, miny);

    return obj;
}

var relativeBoundingBoxProto = {
    contains: function(p) {
        this.updateBounds();
        return (p.x >= this.minx && p.y >= this.miny &&
                p.x <= this.maxx && p.y <= this.maxy);
    },

    updateBounds: function() {
        var minx, miny;
        minx = this.relative_to.getMinX();
        miny = this.relative_to.getMinY();

        this.minx = minx + this.dx;
        this.miny = miny + this.dy;
        this.maxx = minx + this.dx + this.width;
        this.maxy = miny + this.dy + this.height;
    },

    moveBy: function(x,y) {
        this.dx += x;
        this.dy += y;

        this.updateBounds();
    },

    getMinX: function() { this.updateBounds(); return this.minx; },
    getMinY: function() { this.updateBounds(); return this.miny; },
    getMaxX: function() { this.updateBounds(); return this.maxx; },
    getMaxY: function() { this.updateBounds(); return this.maxy; },
    getMidX: function() { this.updateBounds(); return this.minx+this.width/2; },
    getMidY: function() { this.updateBounds(); return this.miny+this.height/2; },
};

function constructRelativeBB(relative_to, dx, dy, width, height) {
    var F = function(){};
    F.prototype = relativeBoundingBoxProto;
    obj = new F;

    obj.relative_to = relative_to;
    obj.dx = dx;
    obj.dy = dy;
    obj.width = width;
    obj.height = height;
    obj.updateBounds();

    return obj;
}

//
function constructButton(args) {
    var text, callback, bb;

    var that;
    
    that = {};

    text        = args.text;
    bb          = args.bb;
    callback    = args.callback;

    that.contains = function(p) {
        return bb.contains(p);
    };

    that.click = function(p) {
        callback(p);
    };

    that.render = function(context) {
        context.fillStyle = "#000";
        context.fillRect(bb.minx, bb.miny, bb.width, bb.height);

        context.font = "bold 12px sans-serif";
        context.fillStyle = "white";
        context.textBaseline = "middle";
        context.textAlign = "center";
        context.fillText(text, bb.minx+bb.width/2, bb.miny+bb.height/2);
    };

    return that;
}

//
function constructIOArray(parent_bb, x, y, w, h) {
    var i, j;
    var first_arg = 5;  // 5 fixed args
    var a = [];
    a.names = [];
    a.bounds = [];
    a.selected = -1;
    a.indexes = {};

    for (i = 0; i < (arguments.length-first_arg)/2; i++) {
        j = i*2 + first_arg;
        a.names[i] = arguments[j];
        a.indexes[arguments[j]] = i;
        a.bounds[i] = constructRelativeBB(parent_bb, x, y+h*i, w, h);

        a[i] = arguments[j+1];
    }

    return a;
}

function constructNode(title) {
    var that;
    var bb, title_bb;
    var last_updated, update_in_progress;

    bb = constructBB(100,100,100,200);
    title_bb = constructRelativeBB(bb, 0, 0, 100, 20);

    that = {};
    that.inputs = [];
    that.outputs = [];

    that.bb = bb;
    
    that.render = function (context, t, dt, selected) {
        context.fillStyle = "black";
        context.fillRect(bb.minx, bb.miny, bb.width, bb.height);

        context.fillStyle = "white";
        context.fillRect(title_bb.minx+2, title_bb.miny+2, title_bb.width-4, title_bb.height-4);

        context.fillStyle = "black";
        context.font = "bold 12px sans-serif";
        context.textBaseline = "middle";
        context.textAlign = "center";
        title_bb.updateBounds();
        context.fillText(title, title_bb.minx+title_bb.width/2, title_bb.miny+title_bb.height/2);

        drawIO(context, selected, that.inputs, bb.minx+2, 40);
        drawIO(context, selected, that.outputs, bb.maxx-2-40, 40);
    };

    var drawIO = function(context, selected, list, x, width) {
        var i, name, iobb;
        for (i = 0; i < list.length; i++) {
            name = list.names[i];
            iobb = list.bounds[i];

            iobb.updateBounds();

            if (selected && list.selected == i) {
                context.fillStyle = "red";
            } else {
                context.fillStyle = "white";
            }
            context.fillRect(iobb.minx+2, iobb.miny+2, iobb.width-4, iobb.height-4);

            context.fillStyle = "black";
            context.font = "12px sans-serif";
            context.textBaseline = "top";
            context.textAlign = "left";
            context.fillText(name, iobb.minx+2, iobb.miny+2);
        }
    };

    that.contains = function(p) {
        return bb.contains(p);
    };

    that.checkHitTitle = function(p) {
        return title_bb.contains(p);
    };

    var checkHitIO = function(list, p) {
        var i;

        for (i = 0; i < list.length; i++) {
            if (list.bounds[i].contains(p)) {
                return i;
            }
        }
        return null;
    };

    that.checkHitInput = function(p) {
        return checkHitIO(this.inputs, p);
    };
    
    that.checkHitOutput = function(p) {
        return checkHitIO(this.outputs, p);
    };

    that.pickup = function(p) {
        lastpos = p;
    };

    that.drag = function(p) {
        bb.moveBy(p.x-lastpos.x, p.y-lastpos.y);
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

    that.render = function (context, t, dt, selected) {
        super_render(context, dt, dt, selected);
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

    that.inputs = constructIOArray(that.bb, 0, 30, 44, 24,
        "period", 1000, "amplitude", 50, "phase", 0, "offset", 0);
    that.outputs = constructIOArray(that.bb, 100-44, 30, 44, 24,
        "osc", 0);
    that.update = function (context, t, dt) {
        gset("osc", Math.sin((t+g('phase'))/g('period')*Math.PI*2)*g('amplitude')+g('offset'));
    };

    return that;
}

function constructImageNode() {
    var that;
    var super_render;

    that = constructNode("circle!");
    
    super_render = that.render;

    that.render = function (context, t, dt, selected) {
        super_render(context, t, dt, selected);

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
        context.fillStyle = g('color');
        context.beginPath();
        context.arc(g('x')+128, g('y')+128, g('size')*30, 0, Math.PI*2, false);
        context.closePath();
        context.fill();
    };

    that.inputs = constructIOArray(that.bb, 0, 30, 44, 24,
        "x", 0, "y", 0, "size", 1.0, "color", "red");

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
var prompt_element;
var prompt_listener;
var prompt_callback;
var prompting_node;

that = {};

dragging = null;
active_pipe = {};

buttons = [];
nodes = [];
pipes = [];

buttons = [
    constructButton({
        text:   "circle",
        bb:     constructBB(0,100,70,50),
        callback: function() {
            nodes.push(constructImageNode());
        }
    }),

    constructButton({
        text:   "wave",
        bb:     constructBB(0,170,70,50),
        callback: function() {
            nodes.push(constructWaveNode());
        }
    }),

    constructButton({
        text:   "unParse",
        bb:     constructBB(0,240,70,50),
        callback: function() {
            unParse();
        }
    }),

    constructButton({
        text:   "reset",
        bb:     constructBB(0,310, 70, 50),
        callback: function() {
            nodes = [];
            pipes = [];
        }
    }),
];

prompt_element = document.getElementById(prompt_id);
prompt_listener = function () {
    if (typeof prompt_callback == 'function') {
        prompt_callback();
        prompt_callback = null;
    }
    return false;
};
prompt_element.form.addEventListener("submit", prompt_listener, true);

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
    var start_bb, end_bb;
    
    that = {};

    current_point = start_point;
    start_bb = start_node.outputs.bounds[output_idx];
    end_bb = null;

    that.drag = function (p) {
        current_point = p;

        // TODO: would be nice to preview/snap, but we'll need detach to do it
        // right
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

            end_bb = end_node.inputs.bounds[input_idx];
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
            var head_len = 15;

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
                dx = -dx / mag * head_len;
                dy = -dy / mag * head_len;
            }
            
            context.strokeStyle = "blue";
            context.lineWidth = 5;
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

        if (end_bb !== null) {
            drawArrow({start:   {x: start_bb.getMidX(), y: start_bb.getMidY()},
                       end:     {x: end_bb.getMidX(), y: end_bb.getMidY()}
            });
        } else {
            drawArrow({start:   {x: start_bb.getMidX(), y: start_bb.getMidY()},
                       end:     current_point
            });
        }
    };

    return that;
};

var promptForInput = function(submit_callback, current_value) {
    prompt_callback = submit_callback;
    prompt_element.value = current_value;
};

var unParse = function (p) {
};

// public methods
that.mouseclick = function (p) {
    var i, endpoint;

    // nodes are in front of buttons
    for (i = nodes.length-1; i >= 0; i--) {
        if (nodes[i].contains(p)) {

            endpoint = nodes[i].checkHitInput(p);
            if (endpoint !== null) {
                // clicked on an input, we may be able to set it
                // (only purely numeric supported for now)

                if (typeof nodes[i].inputs[endpoint] == 'number') {
                    nodes[i].inputs.selected = endpoint;
                    prompting_node = i;

                    // additional function scope in order to preserve locals
                    promptForInput((function () {
                        var my_i = i, my_endpoint = endpoint;
                        return function () {
                            nodes[my_i].inputs[my_endpoint] = parseFloat(prompt_element.value);
                            nodes[i].inputs.selected = -1;
                        };
                    })(), nodes[i].inputs[endpoint]);
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

that.mousepickup = function (p) {
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

that.mousedrag = function (p) {
    if (!!dragging) {
        dragging.drag(p);
    }
    return true;
};

that.mousedrop = function (p) {
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
    context.strokeStyle="black";
    context.lineWidth=1;
    context.strokeRect(0.5, 0.5, w-0.5, h-0.5);

    // render buttons
    for (i = 0; i < buttons.length; i++) {
        buttons[i].render(context);
    }

    // render nodes
    for (i = 0; i < nodes.length; i++) {
        nodes[i].render(context, t, dt, (i==prompting_node));
    }

    // give some priority to current dragster (could be redundant)
    if (!!dragging && 'render' in dragging && typeof dragging.render == 'function') {
        dragging.render(context, t, dt, (i==prompting_node));
    }

    // render pipes (looks bad to be behind dragged object)
    for (i = 0; i < pipes.length; i++) {
        pipes[i].render(context);
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
        context.strokeStyle="black";
        context.lineWidth=1;
        context.strokeRect(0.5, 0.5, 255.5, 255.5);

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

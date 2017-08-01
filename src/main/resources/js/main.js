const Graph = ForceGraph3D()(document.getElementById("3d-graph"));

Graph.cooldownTicks(200)
    .warmupTicks(100)
    .nameField('id')
    .autoColorBy('group')
    .forceEngine('ngraph')
    .nodeRelSize(0.05)
    .jsonUrl('data.json' + window.location.search);

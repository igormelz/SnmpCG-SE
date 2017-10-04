var configProp = null;
var tagkeys = [];

bootstrap_alert = function() {
}

bootstrap_alert.info = function(message, timeout) {
	$("div#action-success").html("<p>" + message + "</p>");
	$("div#action-success").show();
	$("div#action-success").fadeOut(timeout);
}

bootstrap_alert.error = function(message, timeout) {
	$("div#action-error").html("<p>" + message + "</p>");
	$("div#action-error").show();
	$("div#action-error").fadeOut(timeout);
}

var clusterInfo = function() {
	$.ajax({
		url : "/api/v1/cluster/nodeStatus",
		success : function(answer) {
			$('#clusterInfo').text(answer.Status);
		}
	});
}

function showTagsInfo(tags) {
	var answer = "";
	Object.keys(tags).forEach(
			function(item) {
				if (tags[item]) {
					// skip vlanOidtag
					if (configProp.vlanOidTag == null
							|| configProp.vlanOidTag != item) {
						answer += "<span class='small text-info'><code>" + item
								+ ":</code>&nbsp;" + tags[item]
								+ "</span></br>";
					}
				}
			});
	return answer;
}

var getChargingStats = function() {
	$.ajax({
		url : "/api/v1/interfaces?stats",
		success : function(stats) {
			$("#chargeableCount").text(stats.chargeableCount);
			$("#traceCount").text(stats.traceCount);
		}
	});
}

function getSourceInfo() {
	$.ajax({
		url : "/api/v1/sources/" + ip,
		success : function(source) {
			$("#updated").text(new Date(source.pollTime).toLocaleString());
			$("#sysName").text("(" + source.sysName + ")");
			$("#ifNumberCount").text(source.ifNumber);
			$("#sysUptime").text(uptime(source.sysUptime));
			$("#statusUpCount").text(source.statusUpCounter);
			$("#statusDownCount").text(source.statusDownCounter);
			$("#chargeableCount").text(source.chargeableCounter);
			$("#traceCount").text(source.traceCounter);
		}
	});
}

// fill counter
function fillCounter() {
	$.ajax({
		url : "/api/v1/sources?stats",
		success : function(stats) {
			var up = (stats.SUCCESS) ? parseInt(stats.SUCCESS) : 0;
			var pending = (stats.UNKNOWN) ? parseInt(stats.UNKNOWN) : 0;
			var down = (stats.TIMEOUT) ? parseInt(stats.TIMEOUT) : 0;
			down += (stats.NO_PDU) ? parseInt(stats.NO_PDU) : 0;
			down += (stats.NO_IFTABLE) ? parseInt(stats.NO_IFTABLE) : 0;
			$('#allVal').text(up + pending + down);
			$('#successVal').text(up);
			$('#timeoutVal').text(down);
			$('#unknownVal').text(pending);
		}
	});
}

function addSourceField(name) {
	var field = "<div class='form-group'><label>"
			+ name
			+ ":</label><input id='"
+ name
+ "' name='"
+ name
+ "' type='text' class='form-control' placeholder='field-value'></div>";
	tagkeys.push(name);
	$('#customFields').append(field);
	$('#sourceCustomFields').append(field);
	//console.log("add field " + name);
}

function getSourceConfig() {
	$.ajax({
		url : "/api/v1/sources/config",
		success : function(conf) {
			conf.sourceTagKeys.forEach(function(key) {
				addSourceField(key);
			});
			configProp = conf;
		}
	});
}

function getPortConfig() {
	$.ajax({
		url : "/api/v1/sources/config",
		success : function(conf) {
			conf.interfaceTagKeys.forEach(function(key) {
				addIfField(key);
			});
			configProp = conf;
		}
	});
}

function enableDefaultValue(e) {
	if (e) {
		$('#community').prop('disabled', true);
		$('#community').val(configProp.snmpCommunity);
		$('#retries').prop('disabled', true);
		$('#retries').val(configProp.snmpRetries);
		$('#timeout').prop('disabled', true);
		$('#timeout').val(configProp.snmpTimeout);
	} else {
		$('#community').prop('disabled', false);
		$('#retries').prop('disabled', false);
		$('#timeout').prop('disabled', false);
	}
}

function addslashes(str) {
	return (str + '').trim().replace(/[\\"']/g, '\\$&').replace(/\u0000/g,
			'\\0');
}

// fmtbytes
function fmtBytes(bytes, fmt) {
	if (fmt == null) {
		if (bytes > 1000 * 1000 * 1000) {
			return (bytes / (1000 * 1000 * 1000)).toFixed(1) + ' GB';
		} else if (bytes > 1000 * 1000) {
			return (bytes / (1000 * 1000)).toFixed(1) + ' MB';
		} else if (bytes > 1000) {
			return (bytes / 1000).toFixed(1) + ' kB';
		}
		return bytes + ' B';
	} else {
		switch (fmt) {
		case 'MB':
			return (bytes / (1000 * 1000)).toFixed(1);
		case 'GB':
			return (bytes / (1000 * 1000 * 1000)).toFixed(1);
		case 'kB':
			return (bytes / 1000).toFixed(1);
		default:
			return bytes;
		}
	}
}

// calcRate
function bandwidth(bytes, duration) {
	if (bytes == 0) {
		return 0;
	}
	var bps = (Number.parseInt(bytes) * 800) / duration;
	/*
	 * if (bps > 1000*1000*1000) { return (bps/(1000*1000*1000)).toFixed(1)+"
	 * Gbit/s"; } else if (bps > 1000*1000) { return
	 * (bps/(1000*1000)).toFixed(1)+" Mbit/s"; } else if (bps > 1000) { return
	 * (bps/(1000)).toFixed(1)+" kbit/s"; } return (bps).toFixed(1);
	 */
	return (bps / (1000 * 1000)).toFixed(1);
}

function pad(v) {
	return v < 10 ? "0" + v : v;
}

function validateIPaddress(ipaddress) {
	if (/^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/
			.test(ipaddress)) {
		return (true)
	}
	return (false)
}

function uptime(timeticks) {
	var tt = timeticks;
	var d = Math.floor(tt / 8640000);
	tt %= 8640000;
	var h = Math.floor(tt / 360000);
	tt %= 360000;
	var m = Math.floor(tt / 6000);
	tt %= 6000;
	var s = Math.floor(tt / 100);
	tt %= 100;
	return d + " days, " + pad(h) + ":" + pad(m) + ":" + pad(s) + "." + tt;
}

function getUrlParameter(sParam) {
	var sPageURL = window.location.search.substring(1);
	var sURLVariables = sPageURL.split('&');
	for (var i = 0; i < sURLVariables.length; i++) {
		var sParameterName = sURLVariables[i].split('=');
		if (sParameterName[0] == sParam) {
			return sParameterName[1];
		}
	}
}

function showSourceStatus(status) {
	if (status == 'SUCCESS') {
		return "<lable class='label label-success'><span class=\"glyphicon glyphicon-ok-sign\"></span> Ok</label>"
	}
	if (status == 'UNKNOWN') {
		return "<lable class='label label-info'><span class=\"glyphicon glyphicon-question-sign\"></span> Pending</label>";
	}
	return "<lable class='label label-danger'><span class=\"glyphicon glyphicon-exclamation-sign\"></span> "
			+ status + "</label>";
}

function showSourceDetails(source) {
	if (source.Status == 'UNKNOWN') {
		return "<ul class='list-group'><li class='list-group-item'>waiting for schedule polling source status ...</li></ul>";
	}
	if (source.Status == 'SUCCESS' || source.sysObjectID != null) {
		var answer = "<div class='table-responsive'><table class='table table-striped small'><tbody>";
		answer += "<tr><th width=\"10%\">sysName:</th><td>" + source.sysName
				+ "</td>";
		answer += "<tr><th>sysDescr:</th><td>" + source.sysDescr + "</td>";
		answer += "<tr><th>sysObjectID:</th><td>" + source.sysObjectID
				+ "</td>";
		answer += "<tr><th>sysUpTime:</th><td>" + uptime(source.sysUptime)
				+ "</td>";
		answer += "<tr><th>ifNumber:</th><td>" + source.ifNumber + " ("
				+ source.statusUpCounter + " is up)</td>";
		if  (configProp.vlanOidTag != null) {
			Object.keys(source.tags).forEach(
					function(item) {
						if (source.tags[item]) {
							// skip vlanOidtag
							if (configProp.vlanOidTag == item) {
								answer += "<tr><th><code>" + item
										+ ":</code></th><td>" + source.tags[item]
										+ "</td>";
							}
						}
					});
		}
		answer += "</tbody></table></div>";
		return answer;
	}
	return "<ul class='list-group'><li class='list-group-item'>No info</li></ul>";
}

function fmtIfStatus(s) {
	return (s == 1) ? "Up" : "Down";
}

function showChargeFlow(t) {
	return (t == 0) ? "<span class='label label-primary'><span class='glyphicon glyphicon-cloud-download' title='Download to CPE (Egress)'></span> Downlink</span>"
			: "<lable class='label label-info'><span class='glyphicon glyphicon-cloud-upload' title='Upload on CPE (Ingress)'></span> Uplink</label>";
}


function showPortStatus(s) {
	if (s == 2) {
		return "<lable class='label label-success'><span class=\"glyphicon glyphicon-ok-sign\"></span> Up</label>";
	}
	if (s == 4) {
		return "<label class='label label-default'><span class='glyphicon glyphicon-exclamation-sign' title='down'></span> Disable</label>";
	}
	return "<label class='label label-warning'><span class='glyphicon glyphicon-exclamation-sign' title='down'></span> Down</label>";
}

function showInterfaceDetails(i) {
	var answer = "<div class='table-responsive'><table class='table table-striped small'><tbody>";
	answer += "<tr><th width=\"10%\">Interface Index:</th><td>" + i.ifIndex +
	"</td>";
	answer += "</tr><tr><th width=\"10%\">Alias:</th><td>" + i.ifAlias
			+ "</td>";
	answer += "</tr><tr><th width=\"10%\">Status (Adm/Op):</th><td>"
			+ fmtIfStatus(i.ifAdminStatus) + "/" + fmtIfStatus(i.ifOperStatus)
			+ "</td>";
	answer += "</tr><tr><th width=\"10%\">ifInOctets:</th><td>"
			+ i.ifInOctets.value + " [" + i.ifInOctets.type + "]</td>";
	answer += "</tr><tr><th width=\"10%\">ifOutOctets:</th><td>"
			+ i.ifOutOctets.value + " [" + i.ifOutOctets.type + "]</td>";
	/*
	 * if (i.tags) { Object.keys(i.tags).forEach( function(item) { if
	 * (i.tags[item]) { answer += "<tr><th>Tags:" + item + ":</th><td>" +
	 * i.tags[item] + "</td>"; } }); } if (i.chargeable) { answer += "<tr><th>ChargeFlow</th><td>" +
	 * showChargeFlow(i.chargeFlow) + "</td>"; }
	 */
	answer += "</tbody></table></div>";
	return answer;
}

function addIfField(name) {
	var field = "<div class='input-group'><label>"
			+ name
			+ ":</label><input id='"
			+ name
			+ "' name='"
			+ name
			+ "' type='text' class='form-control' placeholder='field-value'></div>";
	$('#portCustomFields').append(field);
	tagkeys.push(name);
}



function chargeOff(ip, table, stats) {
	var jsonData = JSON.stringify({
		"ifDescr" : $('#ifDescr').val(),
		"chargeable" : false
	});
	console.log("update chargable: " + jsonData);
	$.ajax({
		url : "/api/v1/sources/" + ip + "/interfaces",
		type : "PUT",
		contentType : "application/json",
		data : jsonData,
		success : function(result) {
			table.ajax.reload();
			stats();
			bootstrap_alert.info(result.Status, 2000);
		}
	});
}

function updateChargingInfo(ip, table, stats) {
	var jsonData = "{\"ifDescr\":\"" + $('#ifDescr').val() + "\"";
	jsonData += ",\"chargeable\":true";
	jsonData += ",\"chargeFlow\":" + $('input[name=chargeFlow]:checked').val();

	var tags = [];
	tagkeys.forEach(function(item, idx, arr) {
		var value = $("#portCustomFields input[id='" + item + "']").val();
		if (value != "") {
			tags.push("\"" + item + "\":\"" + addslashes(value) + "\"");
		}
	});

	if (tags.length > 0) {
		jsonData += ",\"tags\":{" + tags.join(",") + "}";
	}

	// jsonData += ",\"tags\":{";
	// tagkeys.forEach(function(item, idx, arr) {
	// jsonData += "\"" + item + "\":\""
	// + addslashes($("#portCustomFields #" + item).val()) + "\"";
	// if (idx != tagkeys.length - 1) {
	// jsonData += ",";
	// }
	// });

	jsonData += "}";
	console.log("try update:" + jsonData);
	$.ajax({
		url : "/api/v1/sources/" + ip + "/interfaces",
		type : "PUT",
		contentType : "application/json",
		data : jsonData,
		success : function(result) {
			table.ajax.reload();
			stats();
			bootstrap_alert.info(result.Status, 2000);
		}
	});
}

function formatSources(source) {
	source.statusInfo = showSourceStatus(source.Status);
	
	source.SourceInfo = "<a title=\"click to view interfaces\" href=\"view.html?ip="
		+ source.ipAddress
		+ "\"><span class=\"glyphicon glyphicon-list\" aria-hidden=\"true\"></span></a>&nbsp;";
	source.SourceInfo += source.ipAddress;
	source.SourceInfo += "<div class='pull-right'>";
	source.SourceInfo += "&nbsp;<a href='#' id='editSrc' title='Change settings' class='small'><span class=\"glyphicon glyphicon-pencil\" aria-hidden=\"true\"></span></a>";
	source.SourceInfo += "&nbsp;<a href='#' id='delSrc' title='Remove source' class='small text-danger'><span class=\"glyphicon glyphicon-trash\" aria-hidden=\"true\"></span></a>";
	source.SourceInfo += "</div>";
	// source.SourceInfo += "</div>";
	// source.SourceInfo += ((source.sysName) ? "<br><small>(" + source.sysName+
	// ")</small>" : "");

	source.sourceTags = showTagsInfo(source.tags);

	// source.counters = "<a title=\"click to view interfaces\"
	// href=\"view.html?ip="+ source.ipAddress + "\"><span class=\"glyphicon
	// glyphicon-list\" aria-hidden=\"true\"></span></a>";
	source.counters = source.chargeableCounter;// + "/" + source.traceCounter;

	if (source.pollTime == 0) {
		source.pollTime = "none";
	} else {
		source.pollTime = new Date(source.pollTime).toLocaleString();
	}
}

function formatSourceInterfaces(ifEntry) {
	ifEntry.chargeInfo = showTagsInfo(ifEntry.tags);
	ifEntry.actionTrace = "";
	ifEntry.action = "";
	ifEntry.chargeFlowInfo = "";

	ifEntry.interfaceInfo = ifEntry.ifDescr; 
	ifEntry.interfaceInfo += "<div class='pull-right'>";
	if (ifEntry.chargeable) {
		ifEntry.interfaceInfo += "<a href='#' id='editChargingInfo' class=\"btn btn-primary btn-xs\" title='Edit ChargingInfo'>";
		ifEntry.interfaceInfo += "<span class=\"glyphicon glyphicon-stats\"></span></a>";
	} else {
		ifEntry.interfaceInfo += "<a title=\"add to charge\" class=\"btn btn-default btn-xs\" id=\"addChargeInfo\">";
		ifEntry.interfaceInfo += "<span class=\"glyphicon glyphicon-stats\" aria-hidden=\"true\"></span></a>";
	}
	ifEntry.interfaceInfo += "</div>";
	// if (ifEntry.ifAlias != "") {
	// ifEntry.interfaceInfo += "<br><small>(" + ifEntry.ifAlias + ")</small>";
	// }

	if (ifEntry.chargeable) {
		if (ifEntry.trace) {
			ifEntry.actionTrace += "<a title=\"click to trace off\" onclick=\"updateIfTrace('"
					+ ifEntry.ifDescr + "',false)\">";
			ifEntry.actionTrace += "<span class=\"glyphicon glyphicon-check\" aria-hidden=\"true\"></span></a>";
		} else {
			ifEntry.actionTrace += "<a title=\"click to trace\" onclick=\"updateIfTrace('"
					+ ifEntry.ifDescr + "',true)\">";
			ifEntry.actionTrace += "<span class=\"glyphicon glyphicon-unchecked\" aria-hidden=\"true\"></span></a>";
		}
		ifEntry.chargeFlowInfo = showChargeFlow(ifEntry.chargeFlow);
	}

	ifEntry.status = showPortStatus(ifEntry.ifAdminStatus + ifEntry.ifOperStatus); //showInterfaceStatus(ifEntry.up);

	ifEntry.circuit = ifEntry.ifDescr

	ifEntry.rate_in = bandwidth(ifEntry.pollInOctets, 30000);
	ifEntry.bytes_in = fmtBytes(ifEntry.pollInOctets);
	ifEntry.bytes_out = fmtBytes(ifEntry.pollOutOctets);
	ifEntry.rate_out = bandwidth(ifEntry.pollOutOctets, 30000);
}

// format charging interfaces
function formatChargingInterface(ifEntry) {

	ifEntry.sourceName = "<a title=\"click to view interfaces\" href=\"view.html?ip="
		+ ifEntry.source.ipAddress + "\">";
	ifEntry.sourceName += "<span class=\"glyphicon glyphicon-list\" aria-hidden=\"true\"></span></a>&nbsp;";
	ifEntry.sourceName += ifEntry.source.ipAddress;
	
	// ifEntry.sourceName += "<br>" + showTagsInfo(ifEntry.source.tags);

	ifEntry.interfaceInfo = ifEntry.ifDescr;
	ifEntry.interfaceInfo += "<div class='pull-right'>";
	ifEntry.interfaceInfo += "<a href='#' id='editChargingInfo' class=\"btn btn-primary btn-xs\" title='Edit ChargingInfo'>";
	ifEntry.interfaceInfo += "<span class=\"glyphicon glyphicon-stats\"></span></a></div>";
	// if (ifEntry.ifAlias != "") {
	// ifEntry.interfaceInfo += "<br><small>(" + ifEntry.ifAlias + ")</small>";
	// }

	// ifEntry.sourceInfo = showTagsInfo(ifEntry.source.tags);

	ifEntry.status = showPortStatus(ifEntry.portStatus); 

	ifEntry.chargeFlowInfo = showChargeFlow(ifEntry.chargeFlow);

	ifEntry.chargeInfo = showTagsInfo(ifEntry.tags)
			+ showTagsInfo(ifEntry.source.tags);

	ifEntry.chargeAction = "<a href='#' id='editChargingInfo' class=\"btn btn-primary btn-xs\" title='Edit ChargingInfo'>";
	ifEntry.chargeAction += "<span class=\"glyphicon glyphicon-stats\"></span></a>&nbsp;";

	ifEntry.traceAction = "<a href='#' title=\"click to trace off\" id='traceBtn'>";
	if (ifEntry.trace) {
		ifEntry.traceAction += "<span class=\"glyphicon glyphicon-check\" aria-hidden=\"true\"></span></a>";
	} else {
		ifEntry.traceAction += "<span class=\"glyphicon glyphicon-unchecked\" aria-hidden=\"true\"></span></a>";
	}

	// swap in out for egress port
	if (ifEntry.chargeFlow == 0) {
		ifEntry.pollInOctets = [ ifEntry.pollOutOctets,
				ifEntry.pollOutOctets = ifEntry.pollInOctets ][0];
	}

	ifEntry.rate_in = bandwidth(ifEntry.pollInOctets, 30000);
	ifEntry.bytes_in = fmtBytes(ifEntry.pollInOctets);
	ifEntry.bytes_out = fmtBytes(ifEntry.pollOutOctets);
	ifEntry.rate_out = bandwidth(ifEntry.pollOutOctets, 30000);
}

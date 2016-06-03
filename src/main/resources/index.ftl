<!DOCTYPE html>
<html>
<head>
	<meta charset="US-ASCII">
		<meta charset="utf-8">
		<meta name="viewport" content="width=device-width, initial-scale=1">
		<!--
	<meta name="viewport" content="width=320, maximum-scale=1.0" />
	-->
	<title>Domotica</title>
<!--	
	<link rel="stylesheet" href="css/Yellow-C.css" />
-->
	<link rel="stylesheet" href="css/DomoticTheme.css" />
	<link rel="stylesheet" href="css/jquery.mobile.icons.min.css" />
	<link rel="stylesheet" href="http://code.jquery.com/mobile/1.4.0/jquery.mobile.structure-1.4.0.min.css" />
	<script src="http://code.jquery.com/jquery-1.10.2.min.js"></script>
	<script src="http://code.jquery.com/mobile/1.4.0/jquery.mobile-1.4.0.min.js"></script>

	<script type="text/javascript" src="js/domo.js"></script>
</head>
<body>
	<div data-role="page" id="pageone" data-theme="a">
		<div data-role="header" >
			<h1>${model.title}</h1>
		</div>

		<div data-role="content" class="ui-content">
			<div data-role="collapsible-set" data-theme="a" data-content-theme="a">

 			<div data-role="collapsible" data-collapsed="true" data-theme="a">
	    	<h4 style="background: rgba(204,244,204,0.9);">Quickies...</h4>
				<div data-role="controlgroup" data-type="horizontal"><!-- ui-mini ui-btn-inline -->
					<button id="veranda" class="ui-btn ui-corner-all"
						onclick="sendQuickie('eten');">Veranda</button>
					<button id="tv" class="ui-btn ui-corner-all"
						onclick="sendQuickie('tv');">TV</button>
					<button id="eco" class="ui-btn ui-corner-all"
						onclick="sendQuickie('eco');">Eco</button>
					<button id="fel" class="ui-btn ui-corner-all"
						onclick="sendQuickie('fel');">FEL</button>
				</div>
			</div>
<!-- TODO ui-group-theme-[a-z] zet theme voor een collapsible -->
			<#list model.groupNames as group>
				<#if group = "Screens">
					<div id="Screens" data-role="collapsible" data-theme="a">
						<h4>${group}</h4>
						<#list model.groupname2infos[group] as act>
							<fieldset class="ui-grid-a">
							<label id="${act.name}_status" for="${act.name}-controlgroup" class="ui-block-a">${act.description} [ ${act.status} ]</label>
							<div class="ui-block-b" id="${act.name}-controlgroup" data-role="controlgroup" data-type="horizontal">
							    <button name="${act.name}" class="ui-btn ui-icon-carat-d ui-btn-icon-notext ui-corner-all" onclick='sendDown(this);'>Down</button>
							    <button name="${act.name}" class="ui-btn ui-icon-carat-u ui-btn-icon-notext ui-corner-all" onclick='sendUp(this);'>Up</button>
							</div>
							</fieldset>
						</#list>
					</div>
				<#else>
					<#if model.groupOn[group]>
					<div id="${group}" data-role="collapsible" data-theme="c">
					<#else>
					<div id="${group}" data-role="collapsible" data-theme="a">
					</#if>
					<!-- TODO lichtgeel als er minstens 1 licht aan is; misschien gradaties van geel om aantal lichten (absoluut) aan te geven? -->
					<h4>${group}</h4>
						<div data-theme="a">
						<#list model.groupname2infos[group] as act>
							<#if act.type = "Switch">
								<button id="${act.name}" name="${act.name}" class="ui-btn ui-corner-all" onclick='sendClick(this, "clicked");'>${act.description}</button>
							<#elseif act.type = "DimmerSwitch">
								<fieldset class="ui-grid-a">
									<label for="${act.name}-controlgroup" class="ui-block-a">${act.description}</label>
									<div class="ui-block-b" id="${act.name}-controlgroup" data-role="controlgroup" data-type="horizontal">
									    <button name="${act.name}" class="ui-btn ui-icon-carat-d ui-btn-icon-notext ui-corner-all" onclick='sendDown(this);'>Down</button>
									    <button name="${act.name}" class="ui-btn ui-icon-carat-u ui-btn-icon-notext ui-corner-all" onclick='sendUp(this);'>Up</button>
									</div>
								</fieldset>
							<#elseif act.type = "ScreenController">
								<label>
									<input type="checkbox" id="${act.name}" name="${act.name}" value="${act.name}" <#if act.on>checked</#if> onclick='sendToggle(this);'>${act.description}</input> 
									${act.status}
								</label>
							<#else>
								<label><!-- TODO Flip Switch ipv checkbox -->
									<input type="checkbox" id="${act.name}" name="${act.name}" value="${act.name}" <#if act.on>checked</#if> onclick='sendToggle(this);'>${act.description}</input> 
								</label>
								<#if act.type = "DimmedLamp">
									<#if act.on><#assign disableslider="false"><#else><#assign disableslider="true"></#if>
									<input type="range" id="${act.name}_lvl" name="${act.name}" min="0" max="100" step="5" value="${act.level}" data-disabled="${disableslider}" onchange='sendLevelDL(this);'/>
								</#if>
							</#if>
						</#list>
						</div>
					</div>
				</#if>
			</#list>
		</div>
		<div data-role="footer" data-theme="b" data-position="fixed" >
			<div class="ui-grid-a">
				<div class="ui-block-a">
			        <select id="autorefreshbutton" name="autorefreshbutton" data-role="slider" >
			            <option value="off">manual</option>
			            <option value="on" selected="selected">auto</option>
			        </select>
			    </div>
			    <div class="ui-block-b">
			        <select id="ro" name="ro" data-role="slider" class="ui-right">
			            <option value="off">wijzigen</option>
			            <option value="on" selected="selected">lezen</option>
			        </select>
			        <!--
					<a href="#" class="ui-btn-right ui-btn ui-btn-inline ui-mini ui-corner-all ui-btn-icon-right ui-icon-refresh" onclick="refreshActuators();">Refresh</a>
					<span class="ui-title">titel</span> 
					-->
				</div>
			</div>
		</div>
	</div>
</body>
</html>
<html>
<head>
    <title>Print HealthIds</title>
    <link rel="shortcut icon" type="image/ico" href="/${ui.contextPath()}/images/openmrs-favicon.ico"/>
    <link rel="icon" type="image/png\" href="/${ui.contextPath()}/images/openmrs-favicon.png"/>

    <%
        ui.includeJavascript("uicommons", "jquery-1.8.3.min.js", Integer.MAX_VALUE)
        ui.includeCss("uicommons", "styleguide/jquery-ui-1.9.2.custom.min.css", Integer.MAX_VALUE - 10)
        ui.includeJavascript("uicommons", "jquery.toastmessage.js", Integer.MAX_VALUE - 20)
        ui.includeJavascript("shrclient", "mustache.js", Integer.MAX_VALUE - 30)
        ui.includeJavascript("shrclient", "jsBarCode.js", Integer.MAX_VALUE - 30)
        ui.includeCss("uicommons", "styleguide/jquery.toastmessage.css", Integer.MAX_VALUE - 20)
        ui.includeCss("shrclient", "printHid.css", Integer.MAX_VALUE - 10)
    %>


    ${ui.resourceLinks()}
</head>

<body>
<script type="text/javascript">
    function onError(error) {
        var message = "Error occurred. Could not perform the action.";
        if (error.status === 401) {
            message = "The user doesn't have privilege to access this page.";
            jQuery('div:not(.errorMessage)').hide();
            jQuery('#heading').appendTo('body');
            jQuery('.errorMessage').appendTo('body');
        }
        jQuery(".errorMessage").text(message);
        jQuery(".errorMessage").show();
    }

    function getDate(date){
        var month = date.getMonth() + 1;
        var day = date.getDate();
        var year = date.getFullYear();

        if(month < 10)
            month = '0' + month.toString();
        if(day < 10)
            day = '0' + day.toString();

        return year + '-' + month + '-' + day;
    }

    function restrictDatesToToday(){
        var dtToday = new Date();

        var maxDate = getDate(dtToday);
        jQuery("#fromDate").attr('max', maxDate);
        jQuery("#toDate").attr('max', maxDate);
    }

    function validateFromDate(){
        var maxFromDate = new Date(jQuery("#toDate").val());
        if (maxFromDate != "") {
            var maxDate = getDate(maxFromDate);
            jQuery("#fromDate").attr('max', maxDate);
        }
    }

    function validateToDate(){
            var minToDate = new Date(jQuery("#fromDate").val());
            if (minToDate != ""){
                var minDate = getDate(minToDate);
                jQuery("#toDate").attr('min', minDate);
            }

    }

    window.translations = window.translations || {};
    var template;
    jQuery(document).ready(function (e) {
        jQuery.ajax({
            type: "GET",
            url: "/openmrs/ws/users/getAll",
            dataType: "json"
        }).done(function (responseData) {
            var userSelect = jQuery('#user');
            var template = userSelect.html();
            Mustache.parse(template);
            var rendered = Mustache.render(template, {"list": responseData});
            userSelect.html(rendered)
        }).fail(onError);

        restrictDatesToToday();

        jQuery("#fromDate").on('change',validateToDate);
        jQuery("#toDate").on('change',validateFromDate);

        jQuery('#getAll').click(function (e) {
            var userId = jQuery('#user').val();
            var fromDate = jQuery('#fromDate').val();
            var toDate = jQuery('#toDate').val();

            jQuery.ajax({
                type: "GET",
                url: "/openmrs/ws/users/" + userId + "/findAllPatients?from=" + fromDate + "&to=" + toDate,
                dataType: "json"
            }).done(function (responseData) {
                var printArea = jQuery('#printArea');
                var infoMessage;
                var noOfHIDCards = responseData.length;
                if(!responseData || noOfHIDCards < 1){
                    infoMessage = "There are no patients registered within the selected period.";
                    jQuery("#info").text(infoMessage).show();
                    printArea.hide();
                    return;
                }
                infoMessage = "Showing " + noOfHIDCards + " Health ID card(s).";
                jQuery("#info").text(infoMessage).show();

                template = template || printArea.html();
                Mustache.parse(template);
                var rendered = Mustache.render(template, {"cards": responseData});
                printArea.html(rendered);
                JsBarcode(".barcode").init();
                printArea.show();
                jQuery('#print').prop('disabled', false);
            }).fail(onError);
        })
    })
</script>

<div id="body-wrapper">
    ${ui.includeFragment("uicommons", "infoAndErrorMessage")}
    <div id="content" class="container">
        <h1 id="heading">Print Patient HIDs</h1>

        <div style="display:none" class="errorMessage"></div>
        <label for="user">Select a user</label>
        <select id="user" class="user">
            <option value="-1" selected="selected">Select a user</option>
            {{#list}}
            <option value='{{id}}'>{{name}}</option>
            {{/list}}
        </select>

        <div class="dateRange">
            <div>
                <label for="fromDate">From</label>
                <input type="date" id="fromDate" onkeydown="return false">
            </div>

            <div>
                <label for="toDate">To</label>
                <input type="date" id="toDate" onkeydown="return false">
            </div>
            <button class="btn" id="getAll">Get All Patients</button>
            <button class="btn" id="print" onclick="window.print()" disabled>Print All</button>
        </div>
        <div id="info"></div>
        <div id="printArea">
            {{#cards}}
            <div class="healthId">
                <div class="patient_details">
                    <img src="${ui.resourceLink("shrclient", "images/gov_logo.jpg")}" alt="dhis_logo"/>

                    <div class="details_1">
                        <label class="name">Name: {{name}}</label>
                        <label class="gender">Gender: {{gender}}</label>
                        <label class="dob">DOB: {{dob}}</label>
                        <label class="issued">Issued Date: {{issuedDate}}</label>
                    </div>

                    <div class="address_details">
                        <div class="address">Address:</div>

                        <div class="value">{{address}}</div>
                    </div>

                </div>

                <div class="hid_details">
                    <svg class="barcode" jsbarcode-height="35px" jsbarcode-format="CODE39"
                         jsbarcode-value="{{hid}}" jsbarcode-textmargin="0"
                         jsbarcode-fontoptions="bold"/>

                </div>
            </div>
            {{/cards}}
        </div>
    </div>
</div>
</body>
</html>



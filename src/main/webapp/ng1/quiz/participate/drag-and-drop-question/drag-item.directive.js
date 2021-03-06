(function () {
    'use strict';

    angular
        .module('artemisApp')
        .directive('dragItem', dragItem);

    function dragItem() {
        return {
            restrict: 'E',
            scope: {
                dragItem: '=dragItem',
                clickDisabled: "=clickDisabled",
                invalid: "=invalid"
            },
            templateUrl: 'ng1/quiz/participate/drag-and-drop-question/drag-item.template.html'
        };
    }

})();

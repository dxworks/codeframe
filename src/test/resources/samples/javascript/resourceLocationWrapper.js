import React from 'react';
import PropTypes from 'prop-types';
import axios from 'axios';
import _ from 'lodash';

import Header from 'components/header';
import ItemHierarchy from 'components/resourceLocation/leftPanel/itemHierarchy';
import ResourceLocationList from 'components/resourceLocation/rightPanel/resourceLocationList';
import AddEditResourceLocation
    from 'components/resourceLocation/rightPanel/resourceLocationForm/addEditResourceLocation';
import SetItemLayout from 'components/resourceLocation/rightPanel/resourceLocationForm/setItemLayout';
import AddEditItem from 'components/resourceLocation/rightPanel/resourceLocationForm/addEditItem';
import ResourceLocationHelper from 'utilities/resourceLocationHelper';
import UrlHelper from 'utilities/urlHelper';
import ReactNotify from 'react-notify';

export default class ResourceLocationWrapper extends React.Component {
    constructor(props, context) {
        super(props, context);

        this.resourceLocationHelper = new ResourceLocationHelper();
        this.urlHelper = new UrlHelper();
        this.state = {
            resourceLocations: {},
            visitLocations: {},
            itemTypes: [],
            isOpen: true,
            activeUuid: null,
            activePage: 'listing',
            pageData: {}
        };

        this.intl = context.intl;
        this.fetchAllResourceLocations = this.fetchAllResourceLocations.bind(this);
        this.fetchAllVisitLocations = this.fetchAllVisitLocations.bind(this);
        this.fetchItemTypes = this.fetchItemTypes.bind(this);
        this.getBody = this.getBody.bind(this);
        this.fetchAllResourceLocations(this);
        this.fetchAllVisitLocations(this);
        this.fetchItemTypes();
    }

    fetchAllResourceLocations(self) {
        axios
            .get(this.urlHelper.apiBaseUrl() + '/location?tag=Resource%20Location&v=full')
            .then(function (resourceLocationsResponse) {
                const resourceLocationUuidList =
                    _.map(resourceLocationsResponse.data.results, (location) => location.uuid);

                const resourceLocations = _.reduce(
                    resourceLocationsResponse.data.results,
                    (acc, curr) => {
                        if (_.includes(resourceLocationUuidList, curr.uuid)) {
                            acc[curr.uuid] = {
                                uuid: curr.uuid,
                                name: curr.name,
                                description: curr.description,
                                parentResourceLocationUuid:
                                    curr.parentLocation != null ? curr.parentLocation.uuid : null,
                                isOpen:
                                    typeof self.state.resourceLocations[curr.uuid] != 'undefined'
                                        ? self.state.resourceLocations[curr.uuid].isOpen
                                        : false,
                                isHigherLevel:
                                    curr.parentLocation != null
                                        ? !_.includes(resourceLocationUuidList, curr.parentLocation.uuid)
                                        : true
                            };
                        }

                        return acc;
                    },
                    {}
                );

                self.setState({resourceLocations: resourceLocations});
            })
            .catch(function (errorResponse) {
                const error = errorResponse.response.data ? errorResponse.response.data.error : errorResponse;
                self.resourceLocationFunctions.notify('error', error.message.replace(/\[|\]/g, ''));
            });
    }

    fetchAllVisitLocations(self) {
        axios
            .get(this.urlHelper.apiBaseUrl() + '/location?tag=Visit%20Location&v=full')
            .then(function (visitLocationsResponse) {
                const visitLocationUuidList =
                    _.map(visitLocationsResponse.data.results, (location) => location.uuid);
                const visitLocations = _.reduce(
                    visitLocationsResponse.data.results,
                    (acc, curr) => {
                        if (_.includes(visitLocationUuidList, curr.uuid)) {
                            acc[curr.uuid] = {
                                uuid: curr.uuid,
                                name: curr.name,
                                description: curr.description
                            };
                        }

                        return acc;
                    },
                    {}
                );
                self.setState({visitLocations: visitLocations});
            })
            .catch(function (errorResponse) {
                const error = errorResponse.response.data ? errorResponse.response.data.error : errorResponse;
                self.resourceLocationFunctions.notify('error', error.message.replace(/\[|\]/g, ''));
            });
    }

    fetchItemTypes() {
        const self = this;
        axios
            .get(this.urlHelper.apiBaseUrl() + '/itemtype', {
                params: {
                    v: 'full'
                }
            })
            .then(function (response) {
                self.setState({
                    itemTypes: response.data.results
                });
            })
            .catch(function (errorResponse) {
                const error = errorResponse.response.data ? errorResponse.response.data.error : errorResponse;
                self.resourceLocationFunctions.notify('error', error.message.replace(/\[|\]/g, ''));
            });
    }

    resourceLocationFunctions = {
        setActiveLocationUuid: (resourceLocationUuid) => {
            this.setState({
                activeUuid: resourceLocationUuid
            });
        },
        getActiveLocationUuid: () => {
            return this.state.activeUuid;
        },
        setState: (data) => {
            this.setState({
                ...data
            });
        },
        getState: () => {
            return this.state;
        },
        reFetchAllResourceLocations: () => {
            this.fetchAllResourceLocations(this);
        },
        getResourceLocations: () => {
            return this.state.resourceLocations;
        },
        getVisitLocations: () => {
            return this.state.visitLocations;
        },
        getItemTypes: () => {
            return this.state.itemTypes;
        },
        getResourceLocationByUuid: (resourceLocationUuid) => {
            return this.resourceLocationHelper.getResourceLocation(
                this.state.resourceLocations,
                resourceLocationUuid
            );
        },
        getParentResourceLocation: (resourceLocationUuid) => {
            return this.resourceLocationHelper.getParentResourceLocation(
                this.state.resourceLocations,
                resourceLocationUuid
            );
        },
        getChildResourceLocations: (resourceLocationUuid) => {
            return this.resourceLocationHelper.getChildResourceLocations(
                this.state.resourceLocations,
                resourceLocationUuid
            );
        },
        notify: (notifyType, message) => {
            const self = this;
            const successText = this.intl.formatMessage({id: 'SUCCESS'});
            const errorText = this.intl.formatMessage({id: 'ERROR'});
            const infoText = this.intl.formatMessage({id: 'INFO'});
            if (notifyType == 'success') {
                self.refs.notificator.success(successText, message, 5000);
            } else if (notifyType == 'error') {
                self.refs.notificator.error(errorText, message, 15000);
            } else {
                self.refs.notificator.error(infoText, message, 5000);
            }
        }
    };

    style = {
        wrapper: {
            marginTop: 10,
            paddingTop: 20,
            borderRadius: 5,
            backgroundColor: '#fff',
            minHeight: 500
        }
    };

    getBody() {
        if (this.state.activePage == 'listing') {
            return (
                <ResourceLocationList
                    activeUuid={this.state.activeUuid}
                    resourceLocationFunctions={this.resourceLocationFunctions}
                />
            );
        } else if (this.state.activePage == 'addEditLocation') {
            return (
                <AddEditResourceLocation
                    operation={this.state.pageData.operation}
                    activeUuid={this.state.activeUuid}
                    resourceLocationFunctions={this.resourceLocationFunctions}
                />
            );
        } else if (this.state.activePage == 'set-layout') {
            return (
                <SetItemLayout
                    activeUuid={this.state.activeUuid}
                    row={this.state.pageData.row}
                    column={this.state.pageData.column}
                    resourceLocationFunctions={this.resourceLocationFunctions}
                />
            );
        } else if (this.state.activePage == 'addEditItem') {
            return (
                <AddEditItem
                    operation={this.state.pageData.operation}
                    layoutColumn={this.state.pageData.layoutColumn}
                    layoutRow={this.state.pageData.layoutRow}
                    item={this.state.pageData.item}
                    itemTypes={this.state.itemTypes}
                    activeUuid={this.state.activeUuid}
                    resourceLocationFunctions={this.resourceLocationFunctions}
                />
            );
        }
    }

    render() {
        return (
            <div>
                <ReactNotify ref="notificator"/>
                <Header path={this.props.match.path}/>
                <div style={this.style.wrapper}>
                    <ItemHierarchy
                        resourceLocationFunctions={this.resourceLocationFunctions}
                        isOpen={this.state.isOpen}
                    />
                    {this.getBody()}
                </div>
            </div>
        );
    }
}

ResourceLocationWrapper.contextTypes = {
    store: PropTypes.object,
    intl: PropTypes.object
};
